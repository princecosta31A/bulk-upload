package com.bulkupload.client;

import java.io.File;
import java.net.SocketTimeoutException;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.dto.ApiErrorResponse;
import com.bulkupload.dto.DocumentUploadTask;
import com.bulkupload.dto.UploadResult;
import com.bulkupload.exception.DocumentUploadException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * HTTP client for uploading documents to the external API.
 * 
 * Responsibilities:
 * - Building multipart/form-data requests
 * - Setting appropriate headers
 * - Handling HTTP responses and errors
 * - Parsing error responses for detailed error information
 * 
 * This class is intentionally separated from the orchestration logic
 * to maintain single responsibility and ease of testing.
 */
@Component
public class DocumentUploadClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadClient.class);

    private final BulkUploadProperties properties;
    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    public DocumentUploadClient(BulkUploadProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initializes the RestTemplate with configured timeouts.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing DocumentUploadClient with endpoint: {}", properties.getUploadEndpoint());
        log.info("Connection timeout: {}ms, Read timeout: {}ms", 
                properties.getConnectionTimeoutMs(), 
                properties.getReadTimeoutMs());
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectionTimeoutMs());
        factory.setConnectionRequestTimeout(properties.getConnectionTimeoutMs());
        
        this.restTemplate = new RestTemplate(factory);
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Uploads a single document to the configured endpoint.
     * 
     * @param task The upload task containing file and metadata information
     * @return UploadResult containing the outcome of the upload attempt
     */
    public UploadResult uploadDocument(DocumentUploadTask task) {
        UploadResult result = new UploadResult();
        result.setTask(task);
        result.setStartedAt(Instant.now());
        
        log.debug("Starting upload for task[{}]: {}", task.getManifestIndex(), task.getFilePath());
        
        try {
            // Build the multipart request
            HttpEntity<MultiValueMap<String, Object>> requestEntity = buildMultipartRequest(task);
            
            // Execute the upload
            ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getUploadEndpoint(),
                requestEntity,
                String.class
            );
            
            // Process successful response
            result.setHttpStatus(response.getStatusCode());
            result.setResponseBody(response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                result.setStatus(UploadResult.Status.SUCCESS);
                log.info("Upload successful for task[{}]: HTTP {}", 
                        task.getManifestIndex(), response.getStatusCode().value());
            } else {
                // Non-2xx but not an exception (shouldn't normally happen with RestTemplate)
                result.setStatus(UploadResult.Status.FAILED);
                result.setLastErrorMessage("Unexpected status: " + response.getStatusCode());
                log.warn("Upload returned non-success status for task[{}]: HTTP {}", 
                        task.getManifestIndex(), response.getStatusCode().value());
            }
            
        } catch (HttpStatusCodeException e) {
            handleHttpStatusCodeException(task, result, e);
        } catch (ResourceAccessException e) {
            handleResourceAccessException(task, result, e);
        } catch (Exception e) {
            handleGenericException(task, result, e);
        }
        
        result.complete();
        return result;
    }

    /**
     * Checks if the upload endpoint is reachable.
     * Useful for pre-flight validation.
     * 
     * @return true if endpoint is reachable
     */
    public boolean isEndpointReachable() {
        try {
            // Attempt a HEAD request or simple GET to check connectivity
            // Note: This depends on the API supporting such requests
            restTemplate.headForHeaders(properties.getUploadEndpoint());
            return true;
        } catch (Exception e) {
            log.warn("Endpoint reachability check failed: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // REQUEST BUILDING
    // ============================================================

    /**
     * Builds a multipart/form-data request for document upload.
     * 
     * The request structure follows RFC 7578 (multipart/form-data):
     * - Part 1: "document" - the actual file (application/octet-stream)
     * - Part 2: "metadata" - JSON metadata (application/json)
     */
    private HttpEntity<MultiValueMap<String, Object>> buildMultipartRequest(DocumentUploadTask task) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        // ======== Part 1: Document File ========
        File file = task.getResolvedFile();
        if (file == null) {
            throw DocumentUploadException.fileReadError(task.getFilePath(), 
                    new IllegalStateException("File not resolved"));
        }
        
        FileSystemResource fileResource = new FileSystemResource(file);
        body.add("document", fileResource);
        log.debug("Added document part: {} ({} bytes)", file.getName(), file.length());
        
        // ======== Part 2: Metadata (as JSON) ========
        String metadataJson = serializeMetadata(task);
        HttpHeaders metadataHeaders = new HttpHeaders();
        metadataHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> metadataPart = new HttpEntity<>(metadataJson, metadataHeaders);
        body.add("metadata", metadataPart);
        log.debug("Added metadata part: {} chars", metadataJson.length());
        
        // ======== Build Request Headers ========
        HttpHeaders requestHeaders = buildRequestHeaders(task);
        
        return new HttpEntity<>(body, requestHeaders);
    }

    /**
     * Serializes task metadata to JSON string.
     */
    private String serializeMetadata(DocumentUploadTask task) {
        try {
            if (task.getMetadata() != null) {
                return objectMapper.writeValueAsString(task.getMetadata());
            }
            return "{}";
        } catch (Exception e) {
            log.warn("Failed to serialize metadata, using empty object: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Builds HTTP headers for the upload request.
     * Applies default headers from configuration, then overlays per-document overrides.
     */
    private HttpHeaders buildRequestHeaders(DocumentUploadTask task) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        // Apply default headers from configuration
        BulkUploadProperties.DefaultHeaders defaults = properties.getDefaultHeaders();
        
        // X-User-Id
        String userId = task.getHeader("X-User-Id");
        if (userId == null) userId = defaults.getUserId();
        if (userId != null) headers.add("X-User-Id", userId);
        
        // X-Tenant-Id
        String tenantId = task.getHeader("X-Tenant-Id");
        if (tenantId == null) tenantId = defaults.getTenantId();
        if (tenantId != null) headers.add("X-Tenant-Id", tenantId);
        
        // X-Workspace-Id
        String workspaceId = task.getHeader("X-Workspace-Id");
        if (workspaceId == null) workspaceId = defaults.getWorkspaceId();
        if (workspaceId != null) headers.add("X-Workspace-Id", workspaceId);
        
        // Cookie (if provided per-document)
        String cookie = task.getHeader("Cookie");
        if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
        
        // Any other custom headers from task
        task.getHeaderOverrides().forEach((name, value) -> {
            if (!headers.containsKey(name) && value != null) {
                headers.add(name, value);
            }
        });
        
        log.debug("Built request headers: {}", headers.keySet());
        return headers;
    }

    // ============================================================
    // ERROR HANDLING
    // ============================================================

    /**
     * Handles HTTP status code exceptions (4xx, 5xx responses).
     * Attempts to parse the response body as an API error.
     */
    private void handleHttpStatusCodeException(DocumentUploadTask task, UploadResult result, 
                                                HttpStatusCodeException e) {
        result.setHttpStatus(e.getStatusCode());
        result.setResponseBody(e.getResponseBodyAsString());
        result.setStatus(UploadResult.Status.FAILED);
        
        // Try to parse error response
        ApiErrorResponse apiError = parseApiError(e.getResponseBodyAsString());
        result.setApiError(apiError);
        
        String errorMessage = apiError != null 
            ? apiError.getEffectiveMessage()
            : e.getStatusText();
        result.setLastErrorMessage("HTTP " + e.getStatusCode().value() + ": " + errorMessage);
        result.setExceptionType(e.getClass().getSimpleName());
        
        log.warn("Upload failed for task[{}] with HTTP {}: {}", 
                task.getManifestIndex(), 
                e.getStatusCode().value(), 
                errorMessage);
    }

    /**
     * Handles resource access exceptions (connection failures, timeouts).
     */
    private void handleResourceAccessException(DocumentUploadTask task, UploadResult result, 
                                                ResourceAccessException e) {
        result.setStatus(UploadResult.Status.FAILED);
        result.setExceptionType(e.getClass().getSimpleName());
        
        Throwable cause = e.getCause();
        if (cause instanceof SocketTimeoutException) {
            result.setLastErrorMessage("Request timed out: " + cause.getMessage());
            log.error("Upload timeout for task[{}]: {}", task.getManifestIndex(), cause.getMessage());
        } else {
            result.setLastErrorMessage("Connection error: " + e.getMessage());
            log.error("Upload connection error for task[{}]: {}", task.getManifestIndex(), e.getMessage());
        }
    }

    /**
     * Handles unexpected generic exceptions.
     */
    private void handleGenericException(DocumentUploadTask task, UploadResult result, Exception e) {
        result.setStatus(UploadResult.Status.ERROR);
        result.setLastErrorMessage("Unexpected error: " + e.getMessage());
        result.setExceptionType(e.getClass().getSimpleName());
        
        log.error("Unexpected error during upload for task[{}]: {}", 
                task.getManifestIndex(), e.getMessage(), e);
    }

    /**
     * Attempts to parse an API error response from JSON.
     * Returns null if parsing fails.
     */
    private ApiErrorResponse parseApiError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(responseBody, ApiErrorResponse.class);
        } catch (Exception e) {
            log.debug("Could not parse error response as ApiErrorResponse: {}", e.getMessage());
            return null;
        }
    }
}
