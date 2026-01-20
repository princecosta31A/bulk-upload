package com.bulkupload.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Type-safe configuration properties for the Bulk Upload Service.
 *
 * All configuration is externalized and validated at startup.
 * Properties are bound from application.properties/yml with prefix "bulk".
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bulk")
@Validated
public class BulkUploadProperties {

    // ============================================================
    // MANIFEST CONFIGURATION
    // ============================================================

    /**
     * Manifest file path containing document metadata and file locations.
     * Can be absolute or relative to project root.
     */
    @NotBlank(message = "Manifest path is required")
    private String manifestPath;

    // ============================================================
    // UPLOAD API CONFIGURATION
    // ============================================================
    
    /**
     * Target endpoint URL for document upload API.
     */
    @NotBlank(message = "Upload endpoint is required")
    private String uploadEndpoint;

    // ============================================================
    // DEFAULT HEADERS (used when not specified per-document)
    // ============================================================
    
    private DefaultHeaders defaultHeaders = new DefaultHeaders();

    // ============================================================
    // REPORT CONFIGURATION
    // ============================================================
    
    /**
     * Directory where upload reports will be written.
     * Created automatically if it doesn't exist.
     */
    @NotBlank(message = "Report directory is required")
    private String reportDir;
    
    /**
     * Report format: JSON or CSV (default: JSON)
     */
    private ReportFormat reportFormat = ReportFormat.JSON;

    // ============================================================
    // RETRY & RESILIENCE CONFIGURATION
    // ============================================================
    
    /**
     * Number of retry attempts for failed uploads (default: 3)
     */
    @Min(value = 1, message = "Retry count must be at least 1")
    private int retryCount = 3;
    
    /**
     * Initial delay between retries in milliseconds (default: 1000ms)
     */
    private long retryDelayMs = 1000;
    
    /**
     * Multiplier for exponential backoff (default: 2.0)
     */
    private double retryBackoffMultiplier = 2.0;

    // ============================================================
    // BEHAVIOR FLAGS
    // ============================================================
    
    /**
     * If true, continue processing remaining documents when a file is missing.
     * If false, stop processing entirely on first missing file.
     */
    private boolean skipMissingFiles = true;
    
    /**
     * If true, continue processing remaining documents when an upload fails.
     * If false, stop processing entirely on first failed upload.
     */
    private boolean continueOnError = true;
    
    /**
     * If true, validate all manifest entries before starting any uploads.
     * Helps catch configuration errors early.
     */
    private boolean preValidateManifest = true;

    // ============================================================
    // HTTP CLIENT CONFIGURATION
    // ============================================================
    
    /**
     * Connection timeout in milliseconds (default: 30000)
     */
    private int connectionTimeoutMs = 30000;
    
    /**
     * Read timeout in milliseconds (default: 60000)
     */
    private int readTimeoutMs = 60000;
    
    /**
     * Maximum file size in bytes that can be uploaded (default: 100MB)
     */
    private long maxFileSizeBytes = 100 * 1024 * 1024;

    // ============================================================
    // KAFKA CONFIGURATION
    // ============================================================

    /**
     * Enable/disable Kafka consumer for asynchronous processing (default: false)
     */
    private boolean kafkaEnabled = false;

    /**
     * Kafka bootstrap servers (comma-separated list of broker addresses)
     */
    private String kafkaBootstrapServers = "localhost:9092";

    /**
     * Kafka topic name for bulk upload messages
     */
    private String kafkaTopic = "bulk-upload-requests";

    /**
     * Kafka consumer group ID
     */
    private String kafkaConsumerGroupId = "bulk-upload-service-consumer";

    // ============================================================
    // NESTED CLASSES
    // ============================================================
    
    /**
     * Default HTTP headers applied to all upload requests unless overridden per-document.
     */
    @Getter
    @Setter
    public static class DefaultHeaders {
        private String userId;
        private String tenantId;
        private String workspaceId;
    }
    
    /**
     * Supported report output formats.
     */
    public enum ReportFormat {
        JSON, CSV
    }
}
