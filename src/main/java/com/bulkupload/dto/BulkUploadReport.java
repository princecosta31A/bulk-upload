package com.bulkupload.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete report of a bulk upload execution.
 * 
 * Contains summary statistics and detailed results for each document.
 */
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
    
    /**
     * Path to the secondary manifest if used.
     */
    private String secondaryManifestPath;

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

    // ============================================================
    // GETTERS AND SETTERS
    // ============================================================
    
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getManifestPath() { return manifestPath; }
    public void setManifestPath(String manifestPath) { this.manifestPath = manifestPath; }

    public String getSecondaryManifestPath() { return secondaryManifestPath; }
    public void setSecondaryManifestPath(String secondaryManifestPath) { this.secondaryManifestPath = secondaryManifestPath; }

    public int getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(int totalDocuments) { this.totalDocuments = totalDocuments; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }

    public List<UploadResult> getResults() { return results; }
    public void setResults(List<UploadResult> results) { this.results = results; }

    public List<UploadResult> getFailures() { return failures; }
    public void setFailures(List<UploadResult> failures) { this.failures = failures; }

    public ExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(ExecutionStatus executionStatus) { this.executionStatus = executionStatus; }

    public String getExecutionErrorMessage() { return executionErrorMessage; }
    public void setExecutionErrorMessage(String executionErrorMessage) { this.executionErrorMessage = executionErrorMessage; }
}
