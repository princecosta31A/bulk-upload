package com.bulkupload.kafka;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.bulkupload.config.BulkUploadProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka producer service for publishing bulk upload requests to Kafka topic.
 *
 * This service is primarily for testing purposes, allowing API requests
 * to publish JSON messages to Kafka asynchronously. The consumer will
 * then pick up and process these messages.
 *
 * The producer is only enabled when bulk.kafkaEnabled=true in configuration.
 */
@Service
@ConditionalOnProperty(name = "bulk.kafkaEnabled", havingValue = "true")
public class BulkUploadKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final BulkUploadProperties properties;
    private final ObjectMapper objectMapper;

    public BulkUploadKafkaProducer(KafkaTemplate<String, String> kafkaTemplate,
                                   BulkUploadProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Publishes a JSON message to the Kafka topic.
     *
     * @param jsonNode The JSON message to publish
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> publishBulkUploadRequest(JsonNode jsonNode) {
        try {
            String message = objectMapper.writeValueAsString(jsonNode);
            String topic = properties.getKafkaTopic();

            log.info("Publishing message to Kafka topic: {}", topic);
            log.debug("Message content: {}", message);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);

            // Add callback to log success or failure
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Message published successfully to topic {} at partition {} with offset {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish message to topic {}: {}", topic, ex.getMessage(), ex);
                }
            });

            return future;

        } catch (Exception e) {
            log.error("Error serializing JSON message for Kafka: {}", e.getMessage(), e);
            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * Publishes a raw JSON string message to the Kafka topic.
     *
     * @param jsonString The JSON string to publish
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> publishBulkUploadRequest(String jsonString) {
        String topic = properties.getKafkaTopic();

        log.info("Publishing message to Kafka topic: {}", topic);
        log.debug("Message content: {}", jsonString);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, jsonString);

        // Add callback to log success or failure
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Message published successfully to topic {} at partition {} with offset {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish message to topic {}: {}", topic, ex.getMessage(), ex);
            }
        });

        return future;
    }
}
