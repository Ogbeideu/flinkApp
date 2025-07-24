package com.flink.async.bedrock;

import com.async.bedrock.functions.ChatAggregator;
import com.amazonaws.flink.async.bedrock.functions.ChatProcessWindowFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.streaming.api.windowing.assigners.SessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.opensearch.OpenSearchClient;
import software.amazon.awssdk.services.opensearch.model.IndexRequest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class ChatStreamingJob {
    private static final Logger LOG = LogManager.getLogger(ChatStreamingJob.class);

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.setProperty("aws.region", "us-east-1");
        kinesisConsumerConfig.setProperty("flink.stream.initpos", "LATEST");

        DataStream<Tuple2<String, String>> inputStream = env
                .addSource(new FlinkKinesisConsumer<>("my-kinesis-stream", new SimpleStringSchema(), kinesisConsumerConfig))
                .map(new MapFunction<String, Tuple2<String, String>>() {
                    @Override
                    public Tuple2<String, String> map(String record) {
                        String userId = extractUserId(record);
                        String message = extractMessage(record);
                        return new Tuple2<>(userId, message);
                    }

                    private String extractUserId(String record) {
                        // Implement logic to extract user ID from record
                        return "userId";
                    }

                    private String extractMessage(String record) {
                        // Implement logic to extract message from record
                        return "message";
                    }
                });

        DataStream<String> sessionMessages = inputStream
                .keyBy(value -> value.f0)
                .window(SessionWindows.withGap(Time.minutes(5)))
                .aggregate(new ChatAggregator(), new ChatProcessWindowFunction());

        sessionMessages
                .keyBy(value -> extractUserIdFromSession(value))  // Assuming you can extract userId from session message
                .map(new AsyncChatBedrockRequest())
                .print();

        env.execute("Chat Streaming Job");
    }

    private static String extractUserIdFromSession(String sessionMessage) {
        // Implement logic to extract userId from session message
        return "userId";
    }

    public static class AsyncChatBedrockRequest extends RichAsyncFunction<String, String> {
        private transient BedrockRuntimeAsyncClient bedrockClient;
        private transient OpenSearchClient openSearchClient;

        @Override
        public void open(Configuration parameters) {
            bedrockClient = BedrockRuntimeAsyncClient.builder().region(Region.US_EAST_1).build();
            openSearchClient = OpenSearchClient.builder().region(Region.US_EAST_1).build();
        }

        @Override
        public void close() {
            if (bedrockClient != null) {
                bedrockClient.close();
            }
            if (openSearchClient != null) {
                openSearchClient.close();
            }
        }

        @Override
        public void asyncInvoke(String sessionMessage, ResultFuture<String> resultFuture) {
            CompletableFuture<InvokeModelResponse> summaryFuture = invokeSummaryModel(sessionMessage);

            summaryFuture.thenCompose(summaryResponse -> {
                String summary = extractSummary(summaryResponse);
                return invokeEmbeddingModel(summary, sessionMessage);
            }).thenAccept(embeddingResponse -> {
                indexToOpenSearch(embeddingResponse, sessionMessage);
                resultFuture.complete(Collections.singleton("Processed: " + sessionMessage));
            }).exceptionally(ex -> {
                LOG.error("Error processing session message", ex);
                resultFuture.complete(Collections.singleton("Failed: " + sessionMessage));
                return null;
            });
        }

        private CompletableFuture<InvokeModelResponse> invokeSummaryModel(String sessionMessage) {
            JSONObject payload = new JSONObject()
                    .put("system", "Summarize the chat conversation")
                    .put("input", sessionMessage);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .body(SdkBytes.fromUtf8String(payload.toString()))
                    .modelId("anthropic.claude-3-haiku-20240307-v1:0")
                    .build();

            return bedrockClient.invokeModel(request);
        }

        private String extractSummary(InvokeModelResponse response) {
            JSONObject responseObject = new JSONObject(response.body().asUtf8String());
            return responseObject.getString("summary");
        }

        private CompletableFuture<InvokeModelResponse> invokeEmbeddingModel(String summary, String sessionMessage) {
            JSONObject payload = new JSONObject()
                    .put("inputText", summary + " " + sessionMessage);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .body(SdkBytes.fromUtf8String(payload.toString()))
                    .modelId("amazon.titan-embed-text-v1")
                    .contentType("application/json")
                    .accept("*/*")
                    .build();

            return bedrockClient.invokeModel(request);
        }

        private void indexToOpenSearch(InvokeModelResponse response, String sessionMessage) {
            JSONObject embeddingResponse = new JSONObject(response.body().asString(StandardCharsets.UTF_8));
            IndexRequest indexRequest = IndexRequest.builder()
                    .index("chat-sessions")
                    .id(sessionMessage)
                    .document(embeddingResponse.toString())
                    .build();

            openSearchClient.index(indexRequest);
        }
    }
}

