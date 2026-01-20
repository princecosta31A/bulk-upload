package com.bulkupload.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents the complete report of a bulk upload execution.
 *
 * Contains summary statistics and detailed results for each document.
 */
@Getter
@Setter
public class BulkUploadReport {

    // ============================================================
    // EXECUTION METADATA
    // ============================================================
    
    /**
     * Unique identifier for this execution run.
     */
    private String executionId;
    
    /**
     * When the bulk upload started.
     */
    private Instant startedAt;
    
    /**
     * When the bulk upload completed.
     */
    private Instant finishedAt;
    
    /**
     * Total execution duration in milliseconds.
     */
    private long totalDurationMs;
    
    /**
     * Path to the manifest file that was processed.
     */
    private String manifestPath;

    // ============================================================
    // SUMMARY STATISTICS
    // ============================================================
    
    /**
     * Total number of documents in the manifest.
     */
    private int totalDocuments;
    
    /**
     * Number of documents successfully uploaded.
     */
    private int successCount;
    
    /**
     * Number of documents that failed to upload.
     */
    private int failedCount;
    
    /**
     * Number of documents skipped (missing files, validation errors, etc.)
     */
    private int skippedCount;
    
    /**
     * Number of documents with errors during processing.
     */
    private int errorCount;
    
    /**
     * Overall success rate as percentage.
     */
    private double successRate;

    // ============================================================
    // DETAILED RESULTS
    // ============================================================
    
    /**
     * Individual results for each document upload attempt.
     */
    private List<UploadResult> results = new ArrayList<>();
    
    /**
     * List of documents that failed (for quick reference).
     */
    private List<UploadResult> failures = new ArrayList<>();

    // ============================================================
    // EXECUTION STATUS
    // ============================================================
    
    /**
     * Overall execution status.
     */
    private ExecutionStatus executionStatus = ExecutionStatus.PENDING;
    
    /**
     * Error message if execution failed entirely.
     */
    private String executionErrorMessage;

    public enum ExecutionStatus {
        /** Execution has not started yet */
        PENDING,
        
        /** Execution is in progress */
        RUNNING,
        
        /** Execution completed with all documents processed */
        COMPLETED,
        
        /** Execution completed with some failures */
        COMPLETED_WITH_ERRORS,
        
        /** Execution was aborted due to critical error */
        ABORTED,
        
        /** Execution failed to start */
        FAILED
    }

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================
    
    /**
     * Adds a result and updates summary statistics.
     */
    public void addResult(UploadResult result) {
        results.add(result);
        
        switch (result.getStatus()) {
            case SUCCESS -> successCount++;
            case FAILED -> {
                failedCount++;
                failures.add(result);
            }
            case SKIPPED_MISSING_FILE, SKIPPED_VALIDATION_ERROR, SKIPPED_ABORT -> skippedCount++;
            case ERROR -> errorCount++;
        }
        
        totalDocuments = results.size();
        updateSuccessRate();
    }
    
    /**
     * Marks the report as started.
     */
    public void markStarted() {
        this.startedAt = Instant.now();
        this.executionStatus = ExecutionStatus.RUNNING;
    }
    
    /**
     * Marks the report as completed.
     */
    public void markCompleted() {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.totalDurationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
        
        if (failedCount > 0 || errorCount > 0) {
            this.executionStatus = ExecutionStatus.COMPLETED_WITH_ERRORS;
        } else {
            this.executionStatus = ExecutionStatus.COMPLETED;
        }
        
        updateSuccessRate();
    }
    
    /**
     * Marks the report as aborted.
     */
    public void markAborted(String reason) {
        this.finishedAt = Instant.now();
        if (startedAt != null) {
            this.totalDurationMs = finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
        this.executionStatus = ExecutionStatus.ABORTED;
        this.executionErrorMessage = reason;
    }
    
    /**
     * Marks the report as failed.
     */
    public void markFailed(String reason) {
        this.finishedAt = Instant.now();
        this.executionStatus = ExecutionStatus.FAILED;
        this.executionErrorMessage = reason;
    }
    
    private void updateSuccessRate() {
        if (totalDocuments > 0) {
            this.successRate = (double) successCount / totalDocuments * 100;
        }
    }
    
    /**
     * Checks if all uploads were successful.
     */
    public boolean isAllSuccess() {
        return failedCount == 0 && errorCount == 0;
    }
    
    /**
     * Gets a summary string for logging.
     */
    public String getSummary() {
        return String.format(
            "BulkUpload[total=%d, success=%d, failed=%d, skipped=%d, errors=%d, rate=%.1f%%]",
            totalDocuments, successCount, failedCount, skippedCount, errorCount, successRate
        );
    }
}
