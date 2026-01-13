package com.bulkupload.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.service.BulkUploadService;

/**
 * REST controller for triggering and monitoring bulk upload operations.
 * 
 * Endpoints:
 * - POST /api/v1/bulk-upload/run - Trigger a bulk upload execution
 * - GET /api/v1/bulk-upload/config - View current configuration
 * - GET /api/v1/bulk-upload/health - Check service health
 */
@RestController
@RequestMapping("/api/v1/bulk-upload")
public class BulkUploadController {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadController.class);

    private final BulkUploadService bulkUploadService;
    private final BulkUploadProperties properties;

    public BulkUploadController(BulkUploadService bulkUploadService, 
                                BulkUploadProperties properties) {
        this.bulkUploadService = bulkUploadService;
        this.properties = properties;
    }

    // ============================================================
    // BULK UPLOAD EXECUTION
    // ============================================================

    /**
     * Triggers a bulk upload execution using the configured manifest.
     * 
     * The process is synchronous - the response is returned only after
     * all uploads are completed and the report is generated.
     * 
     * @return Response containing the path to the generated report
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBulkUpload() {
        log.info("Received request to execute bulk upload");
        
        try {
            // Execute the bulk upload
            String reportPath = bulkUploadService.processManifest();
            
            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "COMPLETED");
            response.put("message", "Bulk upload completed. Check the report for details.");
            response.put("reportPath", reportPath);
            
            log.info("Bulk upload completed. Report: {}", reportPath);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Bulk upload execution failed: {}", e.getMessage(), e);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "FAILED");
            response.put("message", "Bulk upload execution failed");
            response.put("error", e.getMessage());
            
            // Note: We return 200 because the request itself succeeded,
            // even if individual uploads failed. The report contains details.
            // For actual execution errors (manifest not found, etc.),
            // the GlobalExceptionHandler will return appropriate status codes.
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ============================================================
    // CONFIGURATION ENDPOINTS
    // ============================================================

    /**
     * Returns the current configuration (sanitized, no sensitive data).
     * Useful for debugging and verification.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> config = new LinkedHashMap<>();
        
        // Manifest configuration
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("primaryPath", properties.getManifestPath());
        manifest.put("secondaryPath", properties.getSecondaryManifestPath());
        manifest.put("correlationKey", properties.getManifestCorrelationKey());
        config.put("manifest", manifest);
        
        // Upload configuration
        Map<String, Object> upload = new LinkedHashMap<>();
        upload.put("endpoint", properties.getUploadEndpoint());
        upload.put("maxFileSizeBytes", properties.getMaxFileSizeBytes());
        upload.put("connectionTimeoutMs", properties.getConnectionTimeoutMs());
        upload.put("readTimeoutMs", properties.getReadTimeoutMs());
        config.put("upload", upload);
        
        // Retry configuration
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("count", properties.getRetryCount());
        retry.put("delayMs", properties.getRetryDelayMs());
        retry.put("backoffMultiplier", properties.getRetryBackoffMultiplier());
        config.put("retry", retry);
        
        // Behavior configuration
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("skipMissingFiles", properties.isSkipMissingFiles());
        behavior.put("continueOnError", properties.isContinueOnError());
        behavior.put("preValidateManifest", properties.isPreValidateManifest());
        config.put("behavior", behavior);
        
        // Report configuration
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("directory", properties.getReportDir());
        report.put("format", properties.getReportFormat().name());
        config.put("report", report);
        
        // Default headers (IDs only, not showing full values for security)
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("userIdConfigured", properties.getDefaultHeaders().getUserId() != null);
        headers.put("tenantIdConfigured", properties.getDefaultHeaders().getTenantId() != null);
        headers.put("workspaceIdConfigured", properties.getDefaultHeaders().getWorkspaceId() != null);
        config.put("defaultHeaders", headers);
        
        return ResponseEntity.ok(config);
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    /**
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "bulk-upload-service");
        health.put("manifestConfigured", properties.getManifestPath() != null);
        health.put("endpointConfigured", properties.getUploadEndpoint() != null);
        
        return ResponseEntity.ok(health);
    }
}
