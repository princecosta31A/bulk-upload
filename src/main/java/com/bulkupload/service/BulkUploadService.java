package com.bulkupload.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bulkupload.client.DocumentUploadClient;
import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.dto.BulkUploadReport;
import com.bulkupload.dto.DocumentUploadTask;
import com.bulkupload.dto.UploadResult;
import com.bulkupload.parser.ManifestParserService;
import com.bulkupload.report.ReportGeneratorService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Main orchestration service for bulk document uploads.
 * 
 * This service coordinates the entire upload process:
 * 1. Parse manifest file(s) into upload tasks
 * 2. Validate tasks (check file existence, size limits)
 * 3. Execute uploads with retry logic
 * 4. Generate detailed report
 * 
 * Design Decisions:
 * - Separation of concerns: parsing, uploading, and reporting are delegated to specialized services
 * - Configurable behavior: continue-on-error, skip-missing-files, retry policies
 * - Comprehensive logging: trace individual uploads and overall progress
 * - Detailed reporting: capture all outcomes for analysis
 */
@Service
public class BulkUploadService {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadService.class);

    private final BulkUploadProperties properties;
    private final ManifestParserService manifestParser;
    private final DocumentUploadClient uploadClient;
    private final ReportGeneratorService reportGenerator;

    public BulkUploadService(BulkUploadProperties properties,
                             ManifestParserService manifestParser,
                             DocumentUploadClient uploadClient,
                             ReportGeneratorService reportGenerator) {
        this.properties = properties;
        this.manifestParser = manifestParser;
        this.uploadClient = uploadClient;
        this.reportGenerator = reportGenerator;
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Executes the bulk upload process using configured manifest(s).
     * 
     * @return Path to the generated report file
     */
    public String processManifest() {
        String executionId = generateExecutionId();
        log.info("========================================");
        log.info("Starting bulk upload execution: {}", executionId);
        log.info("Manifest: {}", properties.getManifestPath());
        log.info("========================================");

        BulkUploadReport report = new BulkUploadReport();
        report.setExecutionId(executionId);
        report.setManifestPath(properties.getManifestPath());
        report.markStarted();

        try {
            // ======== PHASE 1: Parse Manifest ========
            log.info("[Phase 1/4] Parsing manifest...");
            List<DocumentUploadTask> tasks = manifestParser.parseManifest();
            log.info("Parsed {} document entries from manifest", tasks.size());

            if (tasks.isEmpty()) {
                log.warn("No documents found in manifest. Nothing to upload.");
                report.markCompleted();
                return reportGenerator.generateReport(report);
            }

            // ======== PHASE 2: Validate Tasks ========
            log.info("[Phase 2/4] Validating {} tasks...", tasks.size());
            List<String> validationErrors = validateTasks(tasks, report);
            
            if (properties.isPreValidateManifest() && !validationErrors.isEmpty()) {
                log.warn("Pre-validation found {} issues", validationErrors.size());
                validationErrors.forEach(err -> log.warn("  - {}", err));
            }

            // ======== PHASE 3: Execute Uploads ========
            log.info("[Phase 3/4] Executing uploads...");
            boolean shouldContinue = executeUploads(tasks, report);
            
            if (!shouldContinue) {
                log.warn("Upload execution was aborted");
                report.markAborted("Execution stopped due to error (continueOnError=false)");
            } else {
                report.markCompleted();
            }

            // ======== PHASE 4: Generate Report ========
            log.info("[Phase 4/4] Generating report...");
            String reportPath = reportGenerator.generateReport(report);
            
            // Log summary
            log.info(reportGenerator.generateSummary(report));
            
            return reportPath;

        } catch (Exception e) {
            log.error("Bulk upload execution failed: {}", e.getMessage(), e);
            report.markFailed(e.getMessage());
            
            // Still try to generate a report even on failure
            try {
                return reportGenerator.generateReport(report);
            } catch (Exception reportError) {
                log.error("Failed to generate failure report: {}", reportError.getMessage());
                return "(execution failed: " + e.getMessage() + ")";
            }
        }
    }

    /**
     * Executes the bulk upload process using JSON provided directly via API.
     *
     * @param jsonNode The JSON containing upload tasks
     * @return Path to the generated report file
     */
    public String processUploadFromJson(JsonNode jsonNode) {
        String executionId = generateExecutionId();
        log.info("========================================");
        log.info("Starting bulk upload execution from API JSON: {}", executionId);
        log.info("========================================");

        BulkUploadReport report = new BulkUploadReport();
        report.setExecutionId(executionId);
        report.setManifestPath("API Request (direct JSON)");
        report.markStarted();

        try {
            // ======== PHASE 1: Parse JSON to Tasks ========
            log.info("[Phase 1/4] Parsing JSON to tasks...");
            List<DocumentUploadTask> tasks = manifestParser.parseJsonToTasks(jsonNode);
            log.info("Parsed {} document entries from JSON", tasks.size());

            if (tasks.isEmpty()) {
                log.warn("No documents found in JSON. Nothing to upload.");
                report.markCompleted();
                return reportGenerator.generateReport(report);
            }

            // ======== PHASE 2: Validate Tasks ========
            log.info("[Phase 2/4] Validating {} tasks...", tasks.size());
            List<String> validationErrors = validateTasks(tasks, report);

            if (properties.isPreValidateManifest() && !validationErrors.isEmpty()) {
                log.warn("Pre-validation found {} issues", validationErrors.size());
                validationErrors.forEach(err -> log.warn("  - {}", err));
            }

            // ======== PHASE 3: Execute Uploads ========
            log.info("[Phase 3/4] Executing uploads...");
            boolean shouldContinue = executeUploads(tasks, report);

            if (!shouldContinue) {
                log.warn("Upload execution was aborted");
                report.markAborted("Execution stopped due to error (continueOnError=false)");
            } else {
                report.markCompleted();
            }

            // ======== PHASE 4: Generate Report ========
            log.info("[Phase 4/4] Generating report...");
            String reportPath = reportGenerator.generateReport(report);

            // Log summary
            log.info(reportGenerator.generateSummary(report));

            return reportPath;

        } catch (Exception e) {
            log.error("Bulk upload execution failed: {}", e.getMessage(), e);
            report.markFailed(e.getMessage());

            // Still try to generate a report even on failure
            try {
                return reportGenerator.generateReport(report);
            } catch (Exception reportError) {
                log.error("Failed to generate failure report: {}", reportError.getMessage());
                return "(execution failed: " + e.getMessage() + ")";
            }
        }
    }

    // ============================================================
    // PHASE 2: VALIDATION
    // ============================================================

    /**
     * Validates all tasks and updates their status.
     */
    private List<String> validateTasks(List<DocumentUploadTask> tasks, BulkUploadReport report) {
        List<String> errors = manifestParser.validateTasks(tasks);
        
        // Add skipped results for invalid tasks if pre-validation is enabled
        for (DocumentUploadTask task : tasks) {
            if (!task.isFileValid() && !task.getFileValidationError().isEmpty()) {
                // These will be properly handled during upload phase
                log.debug("Task[{}] validation failed: {}", 
                        task.getManifestIndex(), task.getFileValidationError());
            }
        }
        
        return errors;
    }

    // ============================================================
    // PHASE 3: UPLOAD EXECUTION
    // ============================================================

    /**
     * Executes uploads for all tasks, handling retries and errors.
     * 
     * @return true if processing should continue (or completed), false if aborted
     */
    private boolean executeUploads(List<DocumentUploadTask> tasks, BulkUploadReport report) {
        int totalTasks = tasks.size();
        
        for (int i = 0; i < tasks.size(); i++) {
            DocumentUploadTask task = tasks.get(i);
            
            log.info("Processing task [{}/{}]: {}", 
                    i + 1, totalTasks, 
                    task.getFilePath() != null ? task.getFilePath() : "(no file path)");

            UploadResult result = processTask(task);
            report.addResult(result);
            
            // Check if we should stop processing
            log.info("Should Continue :{}", shouldContinue(result));
            if (!shouldContinue(result)) {
                log.warn("Stopping execution after task[{}] due to error", task.getManifestIndex());
                
                // Mark remaining tasks as skipped
                for (int j = i + 1; j < tasks.size(); j++) {
                    UploadResult skipped = new UploadResult();
                    skipped.setTask(tasks.get(j));
                    skipped.setStatus(UploadResult.Status.SKIPPED_ABORT);
                    skipped.setStartedAt(Instant.now());
                    skipped.complete();
                    report.addResult(skipped);
                }
                
                return false;
            }
            
            // Log progress
            if ((i + 1) % 10 == 0 || i == tasks.size() - 1) {
                log.info("Progress: {}/{} tasks processed ({} successful, {} failed)", 
                        i + 1, totalTasks, 
                        report.getSuccessCount(), 
                        report.getFailedCount());
            }
        }
        
        return true;
    }

    /**
     * Processes a single upload task with retry logic.
     */
    private UploadResult processTask(DocumentUploadTask task) {
        // Handle invalid files
        if (!task.isFileValid()) {
            if (properties.isSkipMissingFiles()) {
                log.warn("Skipping task[{}]: {}", 
                        task.getManifestIndex(), task.getFileValidationError());
                return UploadResult.skippedValidation(task, task.getFileValidationError());
            } else {
                log.error("Cannot process task[{}]: {}", 
                        task.getManifestIndex(), task.getFileValidationError());
                return UploadResult.failure(task, task.getFileValidationError());
            }
        }

        // Execute with retries
        return executeWithRetry(task);
    }

    /**
     * Executes upload with exponential backoff retry logic.
     */
    private UploadResult executeWithRetry(DocumentUploadTask task) {
        int maxAttempts = properties.getRetryCount();
        long delayMs = properties.getRetryDelayMs();
        double backoffMultiplier = properties.getRetryBackoffMultiplier();
        
        UploadResult lastResult = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.debug("Upload attempt {}/{} for task[{}]", 
                    attempt, maxAttempts, task.getManifestIndex());
            
            lastResult = uploadClient.uploadDocument(task);
            lastResult.setAttemptCount(attempt);
            
            // Success - return immediately
            if (lastResult.isSuccess()) {
                return lastResult;
            }
            
            // Check if error is retryable
            if (!isRetryable(lastResult)) {
                log.debug("Error is not retryable for task[{}]", task.getManifestIndex());
                break;
            }
            
            // Not last attempt - wait before retry
            if (attempt < maxAttempts) {
                log.info("Retry {}/{} for task[{}] after {}ms delay", 
                        attempt + 1, maxAttempts, task.getManifestIndex(), delayMs);
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry interrupted for task[{}]", task.getManifestIndex());
                    break;
                }
                
                // Exponential backoff
                delayMs = (long) (delayMs * backoffMultiplier);
            }
        }
        
        // All attempts exhausted
        if (lastResult != null) {
            log.error("All {} attempts failed for task[{}]: {}", 
                    maxAttempts, task.getManifestIndex(), lastResult.getLastErrorMessage());
        }
        
        return lastResult != null ? lastResult : UploadResult.failure(task, "No upload attempted");
    }

    /**
     * Determines if an upload should be retried based on the result.
     */
    private boolean isRetryable(UploadResult result) {
        // Never retry skipped or successful results
        if (result.isSuccess() || result.isSkipped()) {
            return false;
        }
        
        // Check HTTP status for retryable errors
        if (result.getHttpStatus() != null) {
            int status = result.getHttpStatus().value();
            
            // 5xx server errors are retryable
            if (status >= 500 && status < 600) {
                return true;
            }
            
            // 429 Too Many Requests is retryable
            if (status == 429) {
                return true;
            }
            
            // 408 Request Timeout is retryable
            if (status == 408) {
                return true;
            }
            
            // 4xx client errors (except above) are not retryable
            if (status >= 400 && status < 500) {
                return false;
            }
        }
        
        // Check exception type
        if (result.getExceptionType() != null) {
            String exType = result.getExceptionType();
            // Connection and timeout errors are retryable
            if (exType.contains("Timeout") || exType.contains("Connection")) {
                return true;
            }
        }
        
        // Default: retry unknown errors
        return true;
    }

    /**
     * Determines if processing should continue after a task result.
     */
    private boolean shouldContinue(UploadResult result) {
        // Always continue on success
        if (result.isSuccess()) {
            return true;
        }
        
        // Continue on skipped (handled by skipMissingFiles)
        if (result.isSkipped()) {
            return true;
        }
        
        // Check configuration for failure handling
        return properties.isContinueOnError();
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    /**
     * Generates a unique execution ID.
     */
    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
