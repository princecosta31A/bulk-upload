package com.bulkupload.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.bulkupload.service.BulkUploadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka consumer for asynchronous bulk upload processing.
 *
 * This consumer listens to a configured Kafka topic and processes incoming
 * JSON messages containing document upload requests. Each message is processed
 * independently using the same validation, upload, and reporting pipeline
 * as the synchronous API endpoints.
 *
 * The consumer is only enabled when bulk.kafkaEnabled=true in configuration.
 */
@Component
@ConditionalOnProperty(name = "bulk.kafkaEnabled", havingValue = "true")
public class BulkUploadKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadKafkaConsumer.class);

    private final BulkUploadService bulkUploadService;
    private final ObjectMapper objectMapper;

    public BulkUploadKafkaConsumer(BulkUploadService bulkUploadService) {
        this.bulkUploadService = bulkUploadService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Kafka message listener that processes bulk upload requests.
     *
     * @param message The JSON message containing upload tasks
     * @param partition The Kafka partition this message came from
     * @param offset The message offset in the partition
     */
    @KafkaListener(
        topics = "${bulk.kafkaTopic}",
        groupId = "${bulk.kafkaConsumerGroupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBulkUploadRequest(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("========================================");
        log.info("Received Kafka message from partition {} at offset {}", partition, offset);
        log.info("========================================");

        try {
            // Parse JSON message
            JsonNode jsonNode = objectMapper.readTree(message);
            log.debug("Successfully parsed JSON message");

            // Process the upload request using the existing service
            String reportPath = bulkUploadService.processUploadFromJson(jsonNode);

            log.info("Kafka message processed successfully. Report: {}", reportPath);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Failed to process Kafka message from partition {} at offset {}: {}",
                    partition, offset, e.getMessage(), e);
            log.error("Message content: {}", message);
            log.info("========================================");

            // Note: In a production system, you might want to:
            // 1. Send the message to a dead-letter queue (DLQ)
            // 2. Store the error in a database for later analysis
            // 3. Send an alert/notification
            // For now, we just log the error and continue processing other messages
        }
    }
}
