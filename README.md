# Real-Time Chat Processing with Amazon Managed Service for Apache Flink & Amazon Bedrock

A real-time data processing application that ingests chat messages from Amazon Kinesis Data Stream, processes it using Amazon Managed Service for Apache Flink, generates summaries using Amazon Bedrock, and indexes the results in Amazon OpenSearch Service for enhanced searchability.

## Architecture Overview

```
Amazon Kinesis Data Stream →Amazon  Managed Service for Apache FFlink Processing  Amazon→ Bedrocusing AnthropicI (Claude/Tita model for summary n) Amazon → OpenSearch Indexing
```

## Key Features

- **Real-time Stream Processing**: Processes chat messages in real-time using Apache Flink
- **Session-based Windowing**: Groups messages by user sessions with 5-minute gap windows
- **AI-Powered Summarization**: Leverages Amazon Bedrock's Claude model for intelligent chat summarization
- **Vector Embeddings**: Generates embeddings using Amazon Titan for semantic search capabilities
- **Async Processing**: Non-blocking operations for optimal performance and scalability
- **OpenSearch Integration**: Indexes processed data for fast retrieval and analytics

## Technology Stack

- **Stream Processing**: Apache Flink
- **Data Ingestion**: Amazon Kinesis Data Streams
- **AI/ML Services**: Amazon Bedrock (Claude 3 Haiku, Titan Embed)
- **Search & Analytics**: Amazon OpenSearch Service
- **Build Tool**: Maven
- **Language**: Java 8+

## Prerequisites

- Java 8 or higher
- Maven 3.6+
- AWS CLI configured with appropriate credentials
- AWS services setup:
  - Kinesis Data Stream named `my-kinesis-stream`
  - Bedrock access enabled for Claude and Titan models
  - OpenSearch cluster with `chat-sessions` index

## AWS Permissions Required

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "kinesis:DescribeStream",
                "kinesis:GetShardIterator",
                "kinesis:GetRecords",
                "kinesis:ListShards",
                "bedrock:InvokeModel",
                "es:ESHttpPost",
                "es:ESHttpPut"
            ],
            "Resource": "*"
        }
    ]
}
```

## Project Structure

```
src/
├── main/
│   └── java/
│       └── com/flink/async/bedrock/
│           ├── ChatStreamingJob.java          # Main application entry point
│           └── functions/
│               ├── ChatAggregator.java        # Session message aggregation
│               └── ChatProcessWindowFunction.java # Window processing logic
└── resources/
    └── log4j2.xml                            # Logging configuration
```

## Configuration

Update the following configurations in `ChatStreamingJob.java`:

```java
// Kinesis Configuration
kinesisConsumerConfig.setProperty("aws.region", "us-east-1");
kinesisConsumerConfig.setProperty("flink.stream.initpos", "LATEST");

// Model IDs
"anthropic.claude-3-haiku-20240307-v1:0"  // Summary model
"amazon.titan-embed-text-v1"              // Embedding model
```

## Running the Application

### Local Development
```bash
# Build the project
mvn clean compile

# Run the Flink job
mvn exec:java -Dexec.mainClass="com.flink.async.bedrock.ChatStreamingJob"
```

### Production Deployment
```bash
# Package the application
mvn clean package

# Submit to Amazon Managed Service for Apache Flink
Upload packaged jar file to Amazon S3 Bucket and run flink Application with Amazon Managed Service for Apache flink 
com.flink.async.bedrock.ChatStreamingJob target/flink-chat-processor-1.0.jar
```

## Data Flow

1. **Ingestion**: Chat messages are consumed from Amazon Kinesis Data Stream
2. **Parsing**: Messages are parsed to extract user ID and content
3. **Windowing**: Messages are grouped by user sessions (5-minute gaps)
4. **Aggregation**: Session messages are aggregated for context
5. **AI Processing**: 
   - Claude generates conversation summaries
   - Titan creates vector embeddings
6. **Indexing**: Results are stored in OpenSearch for retrieval

## Performance Characteristics

- **Throughput**: Handles thousands of messages per second
- **Latency**: Sub-second processing for real-time responses
- **Scalability**: Horizontally scalable with Flink's distributed architecture
- **Fault Tolerance**: Built-in checkpointing and recovery mechanisms

## Monitoring & Observability

- Comprehensive logging with Log4j2
- Flink Web UI for job monitoring
- Amazon CloudWatch integration for metrics

## Future Enhancements

- [ ] Add support for multi-language chat processing
- [ ] Implement custom chat intent classification
- [ ] Add real-time alerting for sensitive content detection
- [ ] Integrate with AWS Lambda for serverless scaling
- [ ] Add support for batch processing historical data

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

**Developer**: Ogbeide Uwagboe  
**Email**: silvaworld@yahoo.com
**LinkedIn**: www.linkedin.com/in/ogbeide-uwagboe  

--- 
