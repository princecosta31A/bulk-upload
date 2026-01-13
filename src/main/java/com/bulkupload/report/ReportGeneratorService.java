package com.bulkupload.report;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.dto.BulkUploadReport;
import com.bulkupload.dto.UploadResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;

/**
 * Service responsible for generating and persisting bulk upload reports.
 * 
 * Supports multiple output formats:
 * - JSON (default): Full structured report with all details
 * - CSV: Simplified tabular format for spreadsheet analysis
 * 
 * Reports include:
 * - Execution metadata (timing, paths, etc.)
 * - Summary statistics
 * - Detailed results for each document
 * - Failure details for troubleshooting
 */
@Service
public class ReportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ReportGeneratorService.class);
    
    private static final DateTimeFormatter FILE_DATE_FORMAT = 
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BulkUploadProperties properties;
    private final ObjectMapper objectMapper;

    public ReportGeneratorService(BulkUploadProperties properties) {
        this.properties = properties;
        
        // Configure ObjectMapper for report serialization
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Ensures the report directory exists on startup.
     */
    @PostConstruct
    public void init() {
        try {
            Path reportDir = Paths.get(properties.getReportDir());
            if (!Files.exists(reportDir)) {
                Files.createDirectories(reportDir);
                log.info("Created report directory: {}", reportDir);
            }
        } catch (IOException e) {
            log.warn("Could not create report directory {}: {}", 
                    properties.getReportDir(), e.getMessage());
        }
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Generates and saves a report for the given bulk upload execution.
     * 
     * @param report The BulkUploadReport containing all execution data
     * @return Path to the generated report file
     */
    public String generateReport(BulkUploadReport report) {
        log.info("Generating report for execution: {}", report.getExecutionId());
        
        try {
            String reportPath = switch (properties.getReportFormat()) {
                case JSON -> generateJsonReport(report);
                case CSV -> generateCsvReport(report);
            };
            
            log.info("Report generated successfully: {}", reportPath);
            return reportPath;
            
        } catch (IOException e) {
            log.error("Failed to generate report: {}", e.getMessage(), e);
            return "(failed to write report: " + e.getMessage() + ")";
        }
    }

    /**
     * Generates a summary string suitable for logging.
     */
    public String generateSummary(BulkUploadReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════╗\n");
        sb.append("║              BULK UPLOAD EXECUTION SUMMARY                  ║\n");
        sb.append("╠════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Execution ID: %-43s ║%n", report.getExecutionId()));
        sb.append(String.format("║ Status:       %-43s ║%n", report.getExecutionStatus()));
        sb.append(String.format("║ Duration:     %-43s ║%n", formatDuration(report.getTotalDurationMs())));
        sb.append("╠════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Total Documents:  %-39d ║%n", report.getTotalDocuments()));
        sb.append(String.format("║ ✓ Successful:     %-39d ║%n", report.getSuccessCount()));
        sb.append(String.format("║ ✗ Failed:         %-39d ║%n", report.getFailedCount()));
        sb.append(String.format("║ ⊘ Skipped:        %-39d ║%n", report.getSkippedCount()));
        sb.append(String.format("║ ⚠ Errors:         %-39d ║%n", report.getErrorCount()));
        sb.append(String.format("║ Success Rate:     %-39.1f%% ║%n", report.getSuccessRate()));
        sb.append("╚════════════════════════════════════════════════════════════╝\n");
        
        // Add failure details if any
        if (!report.getFailures().isEmpty()) {
            sb.append("\nFailed Documents:\n");
            for (UploadResult failure : report.getFailures()) {
                sb.append(String.format("  - [%d] %s: %s%n",
                        failure.getTask().getManifestIndex(),
                        failure.getTask().getFilePath(),
                        failure.getLastErrorMessage()));
            }
        }
        
        return sb.toString();
    }

    // ============================================================
    // JSON REPORT GENERATION
    // ============================================================

    /**
     * Generates a detailed JSON report.
     */
    private String generateJsonReport(BulkUploadReport report) throws IOException {
        String fileName = generateFileName("json");
        Path filePath = Paths.get(properties.getReportDir(), fileName);
        
        // Convert report to a clean map structure for JSON output
        Map<String, Object> reportMap = buildJsonReportMap(report);
        
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            objectMapper.writeValue(writer, reportMap);
        }
        
        return filePath.toString();
    }

    /**
     * Builds the JSON report structure.
     */
    private Map<String, Object> buildJsonReportMap(BulkUploadReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        
        // Metadata section
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionId", report.getExecutionId());
        metadata.put("status", report.getExecutionStatus().name());
        metadata.put("startedAt", formatInstant(report.getStartedAt()));
        metadata.put("finishedAt", formatInstant(report.getFinishedAt()));
        metadata.put("durationMs", report.getTotalDurationMs());
        metadata.put("durationFormatted", formatDuration(report.getTotalDurationMs()));
        metadata.put("manifestPath", report.getManifestPath());
        if (report.getSecondaryManifestPath() != null) {
            metadata.put("secondaryManifestPath", report.getSecondaryManifestPath());
        }
        if (report.getExecutionErrorMessage() != null) {
            metadata.put("errorMessage", report.getExecutionErrorMessage());
        }
        map.put("metadata", metadata);
        
        // Summary section
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDocuments", report.getTotalDocuments());
        summary.put("successful", report.getSuccessCount());
        summary.put("failed", report.getFailedCount());
        summary.put("skipped", report.getSkippedCount());
        summary.put("errors", report.getErrorCount());
        summary.put("successRate", String.format("%.2f%%", report.getSuccessRate()));
        map.put("summary", summary);
        
        // Results section (detailed per-document results)
        List<Map<String, Object>> results = report.getResults().stream()
                .map(this::buildResultMap)
                .collect(Collectors.toList());
        map.put("results", results);
        
        // Failures section (quick reference for failed uploads)
        if (!report.getFailures().isEmpty()) {
            List<Map<String, Object>> failures = report.getFailures().stream()
                    .map(this::buildFailureMap)
                    .collect(Collectors.toList());
            map.put("failures", failures);
        }
        
        return map;
    }

    /**
     * Builds a map representation of an upload result.
     */
    private Map<String, Object> buildResultMap(UploadResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        
        map.put("index", result.getTask().getManifestIndex());
        map.put("documentId", result.getTask().getDocumentId());
        map.put("filePath", result.getTask().getFilePath());
        map.put("status", result.getStatus().name());
        map.put("startedAt", formatInstant(result.getStartedAt()));
        map.put("finishedAt", formatInstant(result.getFinishedAt()));
        map.put("durationMs", result.getDurationMs());
        
        if (result.getHttpStatus() != null) {
            map.put("httpStatus", result.getHttpStatus().value());
        }
        
        if (result.getAttemptCount() > 0) {
            map.put("attempts", result.getAttemptCount());
        }
        
        if (result.getLastErrorMessage() != null) {
            map.put("errorMessage", result.getLastErrorMessage());
        }
        
        if (result.getApiError() != null) {
            Map<String, Object> apiError = new LinkedHashMap<>();
            apiError.put("code", result.getApiError().getErrorCode());
            apiError.put("message", result.getApiError().getEffectiveMessage());
            if (result.getApiError().getTraceId() != null) {
                apiError.put("traceId", result.getApiError().getTraceId());
            }
            map.put("apiError", apiError);
        }
        
        if (result.getResponseBody() != null && result.isSuccess()) {
            map.put("response", result.getResponseBody());
        }
        
        return map;
    }

    /**
     * Builds a simplified map for the failures section.
     */
    private Map<String, Object> buildFailureMap(UploadResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", result.getTask().getManifestIndex());
        map.put("filePath", result.getTask().getFilePath());
        map.put("error", result.getLastErrorMessage());
        if (result.getHttpStatus() != null) {
            map.put("httpStatus", result.getHttpStatus().value());
        }
        return map;
    }

    // ============================================================
    // CSV REPORT GENERATION
    // ============================================================

    /**
     * Generates a CSV report for spreadsheet analysis.
     */
    private String generateCsvReport(BulkUploadReport report) throws IOException {
        String fileName = generateFileName("csv");
        Path filePath = Paths.get(properties.getReportDir(), fileName);
        
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            // Header row
            writer.write("Index,DocumentId,FilePath,Status,HttpStatus,DurationMs,Attempts,ErrorMessage\n");
            
            // Data rows
            for (UploadResult result : report.getResults()) {
                writer.write(String.format("%d,%s,%s,%s,%s,%d,%d,%s%n",
                        result.getTask().getManifestIndex(),
                        escapeCsv(result.getTask().getDocumentId()),
                        escapeCsv(result.getTask().getFilePath()),
                        result.getStatus().name(),
                        result.getHttpStatus() != null ? result.getHttpStatus().value() : "",
                        result.getDurationMs(),
                        result.getAttemptCount(),
                        escapeCsv(result.getLastErrorMessage())
                ));
            }
        }
        
        return filePath.toString();
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    /**
     * Generates a unique filename with timestamp.
     */
    private String generateFileName(String extension) {
        String timestamp = FILE_DATE_FORMAT.format(
                Instant.now().atZone(java.time.ZoneId.systemDefault()));
        return String.format("bulk-upload-report-%s.%s", timestamp, extension);
    }

    /**
     * Formats an Instant as ISO string, or returns empty string if null.
     */
    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    /**
     * Formats duration in human-readable format.
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.2fs", durationMs / 1000.0);
        } else {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    /**
     * Escapes a string for CSV output.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
