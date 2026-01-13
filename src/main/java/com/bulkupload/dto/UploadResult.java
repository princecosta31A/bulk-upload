package com.bulkupload.dto;

import java.time.Instant;

import org.springframework.http.HttpStatusCode;

/**
 * Represents the result of a single document upload attempt.
 * 
 * Captures all relevant information about what happened during upload:
 * - Timing information
 * - HTTP response details
 * - Error information if failed
 * - Retry attempt count
 */
public class UploadResult {

    // ============================================================
    // STATUS ENUM
    // ============================================================
    
    /**
     * Possible outcomes of an upload attempt.
     */
    public enum Status {
        /** Upload completed successfully (2xx response) */
        SUCCESS,
        
        /** Upload failed after all retry attempts */
        FAILED,
        
        /** Upload skipped due to missing file */
        SKIPPED_MISSING_FILE,
        
        /** Upload skipped due to validation error */
        SKIPPED_VALIDATION_ERROR,
        
        /** Upload skipped due to previous failures (continueOnError=false) */
        SKIPPED_ABORT,
        
        /** Unexpected error during processing */
        ERROR
    }

    // ============================================================
    // IDENTIFICATION
    // ============================================================
    
    /**
     * Reference to the original upload task.
     */
    private DocumentUploadTask task;
    
    /**
     * Overall status of the upload.
     */
    private Status status;

    // ============================================================
    // TIMING INFORMATION
    // ============================================================
    
    /**
     * When processing of this task started.
     */
    private Instant startedAt;
    
    /**
     * When processing of this task completed.
     */
    private Instant finishedAt;
    
    /**
     * Total duration in milliseconds.
     */
    private long durationMs;

    // ============================================================
    // HTTP RESPONSE DETAILS
    // ============================================================
    
    /**
     * HTTP status code from the API response.
     * Null if the request never reached the server.
     */
    private HttpStatusCode httpStatus;
    
    /**
     * Raw response body from the API.
     * Contains success response or error details.
     */
    private String responseBody;
    
    /**
     * Parsed error response if available.
     */
    private ApiErrorResponse apiError;

    // ============================================================
    // RETRY INFORMATION
    // ============================================================
    
    /**
     * Number of retry attempts made.
     */
    private int attemptCount;
    
    /**
     * Last error message if failed.
     */
    private String lastErrorMessage;
    
    /**
     * Exception class name if error was due to exception.
     */
    private String exceptionType;

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================
    
    /**
     * Checks if the upload was successful.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /**
     * Checks if the upload was skipped (not attempted).
     */
    public boolean isSkipped() {
        return status == Status.SKIPPED_MISSING_FILE 
            || status == Status.SKIPPED_VALIDATION_ERROR
            || status == Status.SKIPPED_ABORT;
    }
    
    /**
     * Marks this result as completed and calculates duration.
     */
    public void complete() {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.durationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }
    
    /**
     * Creates a success result.
     */
    public static UploadResult success(DocumentUploadTask task, HttpStatusCode status, String responseBody) {
        UploadResult result = new UploadResult();
        result.task = task;
        result.status = Status.SUCCESS;
        result.httpStatus = status;
        result.responseBody = responseBody;
        return result;
    }
    
    /**
     * Creates a failure result.
     */
    public static UploadResult failure(DocumentUploadTask task, String errorMessage) {
        UploadResult result = new UploadResult();
        result.task = task;
        result.status = Status.FAILED;
        result.lastErrorMessage = errorMessage;
        return result;
    }
    
    /**
     * Creates a skipped result for missing file.
     */
    public static UploadResult skippedMissingFile(DocumentUploadTask task) {
        UploadResult result = new UploadResult();
        result.task = task;
        result.status = Status.SKIPPED_MISSING_FILE;
        result.lastErrorMessage = "File not found: " + task.getFilePath();
        return result;
    }
    
    /**
     * Creates a skipped result for validation error.
     */
    public static UploadResult skippedValidation(DocumentUploadTask task, String validationError) {
        UploadResult result = new UploadResult();
        result.task = task;
        result.status = Status.SKIPPED_VALIDATION_ERROR;
        result.lastErrorMessage = validationError;
        return result;
    }

    // ============================================================
    // GETTERS AND SETTERS
    // ============================================================
    
    public DocumentUploadTask getTask() { return task; }
    public void setTask(DocumentUploadTask task) { this.task = task; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public HttpStatusCode getHttpStatus() { return httpStatus; }
    public void setHttpStatus(HttpStatusCode httpStatus) { this.httpStatus = httpStatus; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public ApiErrorResponse getApiError() { return apiError; }
    public void setApiError(ApiErrorResponse apiError) { this.apiError = apiError; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
}
