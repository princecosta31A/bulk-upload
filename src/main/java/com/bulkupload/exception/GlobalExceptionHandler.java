package com.bulkupload.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Bulk Upload Service.
 * 
 * Converts exceptions into RFC 7807 compliant error responses.
 * Ensures consistent error format across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BASE_TYPE_URI = "https://teamsync.example.com/errors/bulk-upload/";

    // ============================================================
    // CUSTOM EXCEPTION HANDLERS
    // ============================================================

    /**
     * Handles manifest parsing exceptions.
     * Returns 400 Bad Request for most parsing errors.
     */
    @ExceptionHandler(ManifestParseException.class)
    public ResponseEntity<Map<String, Object>> handleManifestParseException(ManifestParseException ex) {
        log.error("Manifest parsing failed: {}", ex.toString(), ex);

        HttpStatus status = determineStatusForManifestError(ex);
        Map<String, Object> body = buildErrorResponse(
            BASE_TYPE_URI + "manifest-parse-error",
            "Manifest Parse Error",
            status.value(),
            ex.getMessage(),
            ex.getErrorCode(),
            ex.getManifestPath()
        );

        return ResponseEntity.status(status).body(body);
    }

    /**
     * Handles document upload exceptions.
     * Propagates the original API error status when available.
     */
    @ExceptionHandler(DocumentUploadException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentUploadException(DocumentUploadException ex) {
        log.error("Document upload failed: {}", ex.toString(), ex);

        // Use the original HTTP status from the API if available
        HttpStatus status = ex.getHttpStatus() != null 
            ? HttpStatus.resolve(ex.getHttpStatus()) 
            : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Map<String, Object> body = buildErrorResponse(
            BASE_TYPE_URI + "upload-error",
            "Document Upload Error",
            status.value(),
            ex.getMessage(),
            ex.getErrorCode(),
            ex.getDocumentPath()
        );

        // Include original API error details if available
        if (ex.getApiError() != null) {
            body.put("apiError", ex.getApiError());
        }
        if (ex.getResponseBody() != null) {
            body.put("originalResponse", ex.getResponseBody());
        }

        return ResponseEntity.status(status).body(body);
    }

    /**
     * Handles generic bulk upload exceptions.
     */
    @ExceptionHandler(BulkUploadException.class)
    public ResponseEntity<Map<String, Object>> handleBulkUploadException(BulkUploadException ex) {
        log.error("Bulk upload error: {}", ex.toString(), ex);

        Map<String, Object> body = buildErrorResponse(
            BASE_TYPE_URI + "general-error",
            "Bulk Upload Error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ex.getMessage(),
            ex.getErrorCode(),
            ex.getContext()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ============================================================
    // STANDARD EXCEPTION HANDLERS
    // ============================================================

    /**
     * Handles illegal argument exceptions (validation errors).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
            BASE_TYPE_URI + "validation-error",
            "Validation Error",
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            "BULK-0001",
            null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Catches all other exceptions.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        Map<String, Object> body = buildErrorResponse(
            BASE_TYPE_URI + "internal-error",
            "Internal Server Error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred. Please contact support if this persists.",
            "BULK-9999",
            null
        );

        // Include exception type for debugging (but not the full stack trace)
        body.put("exceptionType", ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Builds an RFC 7807 compliant error response.
     */
    private Map<String, Object> buildErrorResponse(String type, String title, int status,
                                                    String detail, String errorCode, String context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("errorCode", errorCode);
        body.put("timestamp", Instant.now().toString());
        
        if (context != null) {
            body.put("context", context);
        }
        
        return body;
    }

    /**
     * Determines appropriate HTTP status for manifest parsing errors.
     */
    private HttpStatus determineStatusForManifestError(ManifestParseException ex) {
        return switch (ex.getErrorCode()) {
            case ManifestParseException.FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ManifestParseException.INVALID_JSON,
                 ManifestParseException.UNSUPPORTED_FORMAT,
                 ManifestParseException.MISSING_REQUIRED_FIELD -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
