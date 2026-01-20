# Kafka Setup Guide for Bulk Upload Service

## Overview

The bulk-upload service now supports **three ways** to process document uploads:

1. **File-based (Synchronous)**: `POST /api/v1/bulk-upload/run` - Reads from configured file
2. **API-based (Synchronous)**: `POST /api/v1/bulk-upload/upload` - Accepts JSON directly
3. **Kafka-based (Asynchronous)**: `POST /api/v1/bulk-upload/publish` - Publishes to Kafka topic

## Configuration

### application.properties

```properties
# Enable Kafka processing
bulk.kafka-enabled=true

# Kafka broker addresses (comma-separated)
bulk.kafka-bootstrap-servers=localhost:9092

# Topic name where messages will be published/consumed
bulk.kafka-topic=bulk-upload-requests

# Consumer group ID
bulk.kafka-consumer-group-id=bulk-upload-service-consumer
```

### Important Notes

- **Kafka is disabled by default** (`bulk.kafka-enabled=false`)
- When disabled, the `/publish` endpoint will return an error
- The Kafka consumer and producer beans are only created when enabled
- All Kafka classes use `@ConditionalOnProperty(name = "bulk.kafkaEnabled", havingValue = "true")`

## Starting Kafka Locally

### Option 1: Using Kafka Scripts

```bash
# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka (in a separate terminal)
bin/kafka-server-start.sh config/server.properties

# Create the topic (optional - will be created automatically)
bin/kafka-topics.sh --create \
  --topic bulk-upload-requests \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

### Option 2: Using Docker

```bash
# Start Kafka with Docker Compose
docker-compose up -d

# Or using a simple Docker command
docker run -d \
  --name kafka \
  -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  confluentinc/cp-kafka:latest
```

## Testing the Kafka Integration

### Step 1: Enable Kafka

Update `application.properties`:
```properties
bulk.kafka-enabled=true
```

### Step 2: Start the Service

```bash
mvn spring-boot:run
```

You should see logs indicating Kafka consumer is active:
```
INFO  c.b.kafka.BulkUploadKafkaConsumer - Kafka consumer initialized
```

### Step 3: Publish a Message

```bash
curl -X POST http://localhost:7009/api/v1/bulk-upload/publish \
  -H "Content-Type: application/json" \
  -d '{
    "requestHeaders": {
      "userId": "693f921ce3ce3818e702e0a1",
      "tenantId": "0e5bd340-7ae5-4be5-b149-d6bea8d1b633",
      "workspaceId": "693f921c44212adcfc25adba"
    },
    "applicationMetadata": {
      "applicationId": "app-001",
      "applicationName": "Test Application"
    },
    "documents": [
      {
        "documentPath": "C:/path/to/document.pdf",
        "metadata": {
          "documentType": "INVOICE",
          "documentName": "Invoice-2024-001.pdf"
        }
      }
    ]
  }'
```

**Response:**
```json
{
  "status": "PUBLISHED",
  "message": "Bulk upload request published to Kafka topic successfully. Processing will happen asynchronously.",
  "topic": "bulk-upload-requests"
}
```

### Step 4: Check Consumer Logs

The consumer will automatically pick up the message and process it:

```
INFO  c.b.kafka.BulkUploadKafkaConsumer - ========================================
INFO  c.b.kafka.BulkUploadKafkaConsumer - Received Kafka message from partition 0 at offset 0
INFO  c.b.kafka.BulkUploadKafkaConsumer - ========================================
INFO  c.b.service.BulkUploadService      - Starting bulk upload execution from API JSON: exec-abc12345
INFO  c.b.service.BulkUploadService      - [Phase 1/4] Parsing JSON to tasks...
INFO  c.b.service.BulkUploadService      - [Phase 2/4] Validating 1 tasks...
INFO  c.b.service.BulkUploadService      - [Phase 3/4] Executing uploads...
INFO  c.b.service.BulkUploadService      - [Phase 4/4] Generating report...
INFO  c.b.kafka.BulkUploadKafkaConsumer - Kafka message processed successfully. Report: C:\Users\...\report.json
```

## Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /publish
       ▼
┌─────────────────────┐
│  BulkUploadController│
└──────┬──────────────┘
       │ publishBulkUploadRequest()
       ▼
┌─────────────────────┐
│ KafkaProducer       │ ──────► Kafka Topic: bulk-upload-requests
└─────────────────────┘
                                       │
                                       │
                                       ▼
                           ┌───────────────────────┐
                           │ KafkaConsumer         │
                           │ (Listening)           │
                           └──────┬────────────────┘
                                  │ processUploadFromJson()
                                  ▼
                           ┌──────────────────────┐
                           │ BulkUploadService    │
                           └──────┬───────────────┘
                                  │
                                  ▼
                    [Parse → Validate → Upload → Report]
```

## Monitoring Kafka

### Check Topic Messages

```bash
# List all topics
bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Consume messages from the topic
bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic bulk-upload-requests \
  --from-beginning
```

### Check Consumer Group Status

```bash
bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group bulk-upload-service-consumer
```

## Production Considerations

### 1. Dead Letter Queue (DLQ)

If a message fails to process, currently it's just logged. For production:
- Implement a DLQ topic for failed messages
- Add retry logic with exponential backoff
- Send alerts for persistent failures

### 2. Concurrency

Adjust consumer concurrency in `KafkaConfig.java`:
```java
factory.setConcurrency(3); // Process 3 messages in parallel
```

### 3. Error Handling

Enhance error handling in `BulkUploadKafkaConsumer.java`:
- Add custom error handlers
- Implement circuit breakers
- Add message replay capability

### 4. Message Persistence

Reports are generated locally. Consider:
- Storing reports in a database
- Sending completion notifications via webhook
- Publishing results to another Kafka topic

### 5. Security

For production Kafka:
```properties
# SSL Configuration
spring.kafka.ssl.trust-store-location=/path/to/truststore.jks
spring.kafka.ssl.trust-store-password=password
spring.kafka.ssl.key-store-location=/path/to/keystore.jks
spring.kafka.ssl.key-store-password=password

# SASL Authentication
spring.kafka.properties.sasl.mechanism=PLAIN
spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="user" password="password";
```

## Troubleshooting

### Issue: "Kafka is not enabled" error

**Solution**: Set `bulk.kafka-enabled=true` in application.properties

### Issue: Connection refused to Kafka

**Solution**:
- Verify Kafka is running: `netstat -an | grep 9092`
- Check `bulk.kafka-bootstrap-servers` is correct
- Ensure firewall allows connections

### Issue: Consumer not receiving messages

**Solution**:
- Check consumer group: `kafka-consumer-groups.sh --describe`
- Verify topic exists: `kafka-topics.sh --list`
- Check application logs for errors
- Ensure `bulk.kafka-enabled=true`

### Issue: Messages processed multiple times

**Solution**:
- Check consumer group offset management
- Ensure proper error handling doesn't cause redelivery
- Verify `ENABLE_AUTO_COMMIT_CONFIG` settings in KafkaConfig

## API Endpoints Summary

| Endpoint | Method | Type | Description |
|----------|--------|------|-------------|
| `/api/v1/bulk-upload/run` | POST | Sync | Process from file |
| `/api/v1/bulk-upload/upload` | POST | Sync | Process from API |
| `/api/v1/bulk-upload/publish` | POST | Async | Publish to Kafka |
| `/api/v1/bulk-upload/config` | GET | - | View configuration |
| `/api/v1/bulk-upload/health` | GET | - | Health check |

## Benefits of Kafka Integration

1. **Asynchronous Processing**: Don't wait for uploads to complete
2. **Scalability**: Add more consumers to process messages in parallel
3. **Reliability**: Messages persist in Kafka even if service is down
4. **Decoupling**: Producers and consumers operate independently
5. **Load Leveling**: Handle traffic spikes by queuing messages
6. **Replay Capability**: Reprocess messages if needed
