package com.bulkupload.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Type-safe configuration properties for the Bulk Upload Service.
 * 
 * All configuration is externalized and validated at startup.
 * Properties are bound from application.properties/yml with prefix "bulk".
 */
@ConfigurationProperties(prefix = "bulk")
@Validated
public class BulkUploadProperties {

    // ============================================================
    // MANIFEST CONFIGURATION
    // ============================================================
    
    /**
     * Primary manifest file path containing document metadata and file locations.
     * Can be absolute or relative to project root.
     */
    @NotBlank(message = "Primary manifest path is required")
    private String manifestPath;
    
    /**
     * Optional secondary manifest path for split manifest scenarios.
     * When provided, this file contains file location mappings that merge
     * with the primary manifest's metadata.
     */
    private String secondaryManifestPath;
    
    /**
     * Key used to correlate documents between primary and secondary manifests.
     * Default is "documentId". When merging manifests, documents are matched
     * using this key.
     */
    private String manifestCorrelationKey = "documentId";

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
    // NESTED CLASSES
    // ============================================================
    
    /**
     * Default HTTP headers applied to all upload requests unless overridden per-document.
     */
    public static class DefaultHeaders {
        private String userId;
        private String tenantId;
        private String workspaceId;
        
        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    }
    
    /**
     * Supported report output formats.
     */
    public enum ReportFormat {
        JSON, CSV
    }

    // ============================================================
    // GETTERS AND SETTERS
    // ============================================================
    
    public String getManifestPath() { return manifestPath; }
    public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }

    public String getSecondaryManifestPath() { return secondaryManifestPath; }
    public void setSecondaryManifestPath(String secondaryManifestPath) { this.secondaryManifestPath = secondaryManifestPath; }

    public String getManifestCorrelationKey() { return manifestCorrelationKey; }
    public void setManifestCorrelationKey(String manifestCorrelationKey) { this.manifestCorrelationKey = manifestCorrelationKey; }

    public String getUploadEndpoint() { return uploadEndpoint; }
    public void setUploadEndpoint(String uploadEndpoint) { this.uploadEndpoint = uploadEndpoint; }

    public DefaultHeaders getDefaultHeaders() { return defaultHeaders; }
    public void setDefaultHeaders(DefaultHeaders defaultHeaders) { this.defaultHeaders = defaultHeaders; }

    public String getReportDir() { return reportDir; }
    public void setReportDir(String reportDir) { this.reportDir = reportDir; }

    public ReportFormat getReportFormat() { return reportFormat; }
    public void setReportFormat(ReportFormat reportFormat) { this.reportFormat = reportFormat; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }

    public boolean isSkipMissingFiles() { return skipMissingFiles; }
    public void setSkipMissingFiles(boolean skipMissingFiles) { this.skipMissingFiles = skipMissingFiles; }

    public boolean isContinueOnError() { return continueOnError; }
    public void setContinueOnError(boolean continueOnError) { this.continueOnError = continueOnError; }

    public boolean isPreValidateManifest() { return preValidateManifest; }
    public void setPreValidateManifest(boolean preValidateManifest) { this.preValidateManifest = preValidateManifest; }

    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
}
