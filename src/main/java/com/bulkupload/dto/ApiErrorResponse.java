package com.bulkupload.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an error response from the upload API.
 * 
 * Designed to capture RFC 7807 (Problem Details) format errors,
 * but flexible enough to handle other error formats as well.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiErrorResponse {

    // ============================================================
    // RFC 7807 STANDARD FIELDS
    // ============================================================
    
    /**
     * A URI reference that identifies the problem type.
     */
    private String type;
    
    /**
     * A short, human-readable summary of the problem type.
     */
    private String title;
    
    /**
     * The HTTP status code.
     */
    private Integer status;
    
    /**
     * A human-readable explanation specific to this occurrence.
     */
    private String detail;
    
    /**
     * A URI reference that identifies the specific occurrence.
     */
    private String instance;

    // ============================================================
    // EXTENDED FIELDS (TeamSync specific)
    // ============================================================
    
    /**
     * Service-specific error code (e.g., "DOC-1001").
     */
    private String errorCode;
    
    /**
     * Trace ID for distributed tracing (OpenTelemetry).
     */
    private String traceId;
    
    /**
     * Timestamp of when the error occurred.
     */
    private String timestamp;
    
    /**
     * Field-level validation errors.
     * Key: field name, Value: error message
     */
    private Map<String, String> fieldErrors;

    // ============================================================
    // LEGACY/ALTERNATIVE ERROR FORMATS
    // ============================================================
    
    /**
     * Alternative field: "message" (common in many APIs)
     */
    private String message;
    
    /**
     * Alternative field: "error" (Spring Boot default)
     */
    private String error;
    
    /**
     * Alternative field: "path" (Spring Boot default)
     */
    private String path;

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================
    
    /**
     * Gets the most appropriate error message from available fields.
     */
    public String getEffectiveMessage() {
        if (detail != null && !detail.isBlank()) return detail;
        if (message != null && !message.isBlank()) return message;
        if (title != null && !title.isBlank()) return title;
        if (error != null && !error.isBlank()) return error;
        return "Unknown error";
    }
    
    /**
     * Checks if this represents a validation error (4xx).
     */
    public boolean isValidationError() {
        return status != null && status >= 400 && status < 500;
    }
    
    /**
     * Checks if this represents a server error (5xx).
     */
    public boolean isServerError() {
        return status != null && status >= 500;
    }

    // ============================================================
    // GETTERS AND SETTERS
    // ============================================================
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(Map<String, String> fieldErrors) { this.fieldErrors = fieldErrors; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    @Override
    public String toString() {
        return String.format("ApiError[status=%d, code=%s, message=%s]",
                status, errorCode, getEffectiveMessage());
    }
}
