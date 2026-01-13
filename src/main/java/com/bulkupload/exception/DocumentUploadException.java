package com.bulkupload.exception;

import com.bulkupload.dto.ApiErrorResponse;

/**
 * Exception thrown when document upload fails.
 * 
 * Contains detailed information about the API error response
 * to enable proper error propagation and reporting.
 */
public class DocumentUploadException extends BulkUploadException {

    private static final String ERROR_CODE_PREFIX = "BULK-2";
    
    public static final String HTTP_ERROR = ERROR_CODE_PREFIX + "001";
    public static final String CONNECTION_ERROR = ERROR_CODE_PREFIX + "002";
    public static final String TIMEOUT_ERROR = ERROR_CODE_PREFIX + "003";
    public static final String FILE_READ_ERROR = ERROR_CODE_PREFIX + "004";
    public static final String SERIALIZATION_ERROR = ERROR_CODE_PREFIX + "005";
    public static final String FILE_TOO_LARGE = ERROR_CODE_PREFIX + "006";
    public static final String INVALID_METADATA = ERROR_CODE_PREFIX + "007";

    private final Integer httpStatus;
    private final String responseBody;
    private final ApiErrorResponse apiError;
    private final String documentPath;

    public DocumentUploadException(String errorCode, String message, String documentPath) {
        super(errorCode, message, documentPath);
        this.httpStatus = null;
        this.responseBody = null;
        this.apiError = null;
        this.documentPath = documentPath;
    }

    public DocumentUploadException(String errorCode, String message, String documentPath, Throwable cause) {
        super(errorCode, message, documentPath, cause);
        this.httpStatus = null;
        this.responseBody = null;
        this.apiError = null;
        this.documentPath = documentPath;
    }

    public DocumentUploadException(String errorCode, String message, String documentPath, 
                                   Integer httpStatus, String responseBody, ApiErrorResponse apiError) {
        super(errorCode, message, documentPath);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.apiError = apiError;
        this.documentPath = documentPath;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public ApiErrorResponse getApiError() {
        return apiError;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    /**
     * Returns true if this error is retryable (e.g., 5xx errors, timeouts).
     */
    public boolean isRetryable() {
        // Connection and timeout errors are retryable
        if (CONNECTION_ERROR.equals(getErrorCode()) || TIMEOUT_ERROR.equals(getErrorCode())) {
            return true;
        }
        // 5xx server errors are retryable
        if (httpStatus != null && httpStatus >= 500 && httpStatus < 600) {
            return true;
        }
        // 429 Too Many Requests is retryable
        if (httpStatus != null && httpStatus == 429) {
            return true;
        }
        return false;
    }

    // ============================================================
    // FACTORY METHODS
    // ============================================================

    public static DocumentUploadException httpError(String documentPath, int httpStatus, 
                                                     String responseBody, ApiErrorResponse apiError) {
        String message = String.format("Upload failed with HTTP %d: %s", 
                httpStatus, 
                apiError != null ? apiError.getEffectiveMessage() : responseBody);
        return new DocumentUploadException(HTTP_ERROR, message, documentPath, httpStatus, responseBody, apiError);
    }

    public static DocumentUploadException connectionError(String documentPath, Throwable cause) {
        return new DocumentUploadException(
            CONNECTION_ERROR,
            "Connection failed: " + cause.getMessage(),
            documentPath,
            cause
        );
    }

    public static DocumentUploadException timeoutError(String documentPath, Throwable cause) {
        return new DocumentUploadException(
            TIMEOUT_ERROR,
            "Request timed out: " + cause.getMessage(),
            documentPath,
            cause
        );
    }

    public static DocumentUploadException fileReadError(String documentPath, Throwable cause) {
        return new DocumentUploadException(
            FILE_READ_ERROR,
            "Failed to read file: " + cause.getMessage(),
            documentPath,
            cause
        );
    }

    public static DocumentUploadException fileTooLarge(String documentPath, long fileSize, long maxSize) {
        return new DocumentUploadException(
            FILE_TOO_LARGE,
            String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)", fileSize, maxSize),
            documentPath
        );
    }

    public static DocumentUploadException invalidMetadata(String documentPath, String details) {
        return new DocumentUploadException(
            INVALID_METADATA,
            "Invalid metadata: " + details,
            documentPath
        );
    }
}
