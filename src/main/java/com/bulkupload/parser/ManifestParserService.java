package com.bulkupload.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.dto.DocumentUploadTask;
import com.bulkupload.exception.ManifestParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service responsible for parsing manifest files into DocumentUploadTask objects.
 *
 * Supports 2 manifest formats:
 *
 * 1. MULTIPLE APPLICATIONS FORMAT:
 *    { "requestHeaders": {...}, "applications": [{ "applicationMetadata": {...}, "documents": [...] }] }
 *
 * 2. SINGLE APPLICATION FORMAT:
 *    { "requestHeaders": {...}, "applicationMetadata": {...}, "documents": [...] }
 */
@Service
public class ManifestParserService {

    private static final Logger log = LoggerFactory.getLogger(ManifestParserService.class);

    private final ObjectMapper objectMapper;
    private final BulkUploadProperties properties;

    public ManifestParserService(BulkUploadProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Parses the configured manifest file into a list of DocumentUploadTask.
     *
     * @return List of parsed upload tasks
     * @throws ManifestParseException if parsing fails
     */
    public List<DocumentUploadTask> parseManifest() {
        log.info("Starting manifest parsing from: {}", properties.getManifestPath());

        // Read and parse manifest file
        JsonNode root = readManifestFile(properties.getManifestPath());

        // Parse JSON to tasks
        return parseJsonToTasks(root);
    }

    /**
     * Parses JSON directly into a list of DocumentUploadTask.
     * This method can be used when JSON is provided via API instead of file.
     *
     * @param root The JSON root node
     * @return List of parsed upload tasks
     * @throws ManifestParseException if parsing fails
     */
    public List<DocumentUploadTask> parseJsonToTasks(JsonNode root) {
        log.debug("Starting JSON parsing to tasks");

        // Extract request headers from root
        JsonNode requestHeaders = root.get("requestHeaders");

        // Detect format and extract applications
        List<DocumentUploadTask> tasks = new ArrayList<>();
        int taskIndex = 0;

        if (root.has("applications")) {
            // Format 1: Multiple applications
            log.debug("Detected format: Multiple applications");
            JsonNode applications = root.get("applications");

            if (!applications.isArray()) {
                throw ManifestParseException.unsupportedFormat(
                    "JSON input",
                    "'applications' field must be an array"
                );
            }

            for (JsonNode application : applications) {
                JsonNode applicationMetadata = application.get("applicationMetadata");
                JsonNode documents = application.get("documents");

                if (documents == null || !documents.isArray()) {
                    log.warn("Application entry missing 'documents' array, skipping");
                    continue;
                }

                for (JsonNode document : documents) {
                    DocumentUploadTask task = convertToTask(document, applicationMetadata, requestHeaders, taskIndex++);
                    tasks.add(task);
                }
            }

        } else if (root.has("applicationMetadata") && root.has("documents")) {
            // Format 2: Single application
            log.debug("Detected format: Single application");
            JsonNode applicationMetadata = root.get("applicationMetadata");
            JsonNode documents = root.get("documents");

            if (!documents.isArray()) {
                throw ManifestParseException.unsupportedFormat(
                    "JSON input",
                    "'documents' field must be an array"
                );
            }

            for (JsonNode document : documents) {
                DocumentUploadTask task = convertToTask(document, applicationMetadata, requestHeaders, taskIndex++);
                tasks.add(task);
            }

        } else {
            throw ManifestParseException.unsupportedFormat(
                "JSON input",
                "JSON must contain either 'applications' array or 'applicationMetadata' with 'documents'"
            );
        }

        log.info("Successfully parsed {} document upload tasks from JSON", tasks.size());
        return tasks;
    }

    /**
     * Validates all tasks before processing begins.
     * 
     * @param tasks List of tasks to validate
     * @return List of validation errors (empty if all valid)
     */
    public List<String> validateTasks(List<DocumentUploadTask> tasks) {
        List<String> errors = new ArrayList<>();
        
        for (DocumentUploadTask task : tasks) {
            // Validate file path is present
            if (task.getFilePath() == null || task.getFilePath().isBlank()) {
                errors.add(String.format("Task[%d]: Missing file path", task.getManifestIndex()));
                task.setFileValid(false);
                task.setFileValidationError("File path not specified");
                continue;
            }
            
            // Validate file exists and is readable
            Path filePath = Paths.get(task.getFilePath());
            File file = filePath.toFile();
            
            if (!file.exists()) {
                errors.add(String.format("Task[%d]: File not found: %s", task.getManifestIndex(), task.getFilePath()));
                task.setFileValid(false);
                task.setFileValidationError("File not found");
            } else if (!file.isFile()) {
                errors.add(String.format("Task[%d]: Path is not a file: %s", task.getManifestIndex(), task.getFilePath()));
                task.setFileValid(false);
                task.setFileValidationError("Path is not a file");
            } else if (!file.canRead()) {
                errors.add(String.format("Task[%d]: File not readable: %s", task.getManifestIndex(), task.getFilePath()));
                task.setFileValid(false);
                task.setFileValidationError("File not readable");
            } else if (file.length() > properties.getMaxFileSizeBytes()) {
                errors.add(String.format("Task[%d]: File too large (%d bytes): %s", 
                        task.getManifestIndex(), file.length(), task.getFilePath()));
                task.setFileValid(false);
                task.setFileValidationError("File exceeds maximum size limit");
            } else {
                task.setFileValid(true);
                task.setResolvedFile(file);
            }
        }
        
        if (!errors.isEmpty()) {
            log.warn("Manifest validation found {} issues", errors.size());
        }
        
        return errors;
    }

    // ============================================================
    // MANIFEST FILE READING
    // ============================================================

    /**
     * Reads and parses a manifest file as JSON.
     */
    private JsonNode readManifestFile(String manifestPath) {
        Path path = Paths.get(manifestPath);

        if (!Files.exists(path)) {
            throw ManifestParseException.fileNotFound(manifestPath);
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            log.debug("Read {} bytes from manifest file", bytes.length);
            return objectMapper.readTree(bytes);
        } catch (IOException e) {
            if (e.getMessage().contains("parse") || e.getMessage().contains("JSON")) {
                throw ManifestParseException.invalidJson(manifestPath, e);
            }
            throw ManifestParseException.ioError(manifestPath, e);
        }
    }

    // ============================================================
    // CONVERSION TO UPLOAD TASKS
    // ============================================================

    /**
     * Converts a document JSON node to a DocumentUploadTask.
     *
     * @param documentNode The document JSON node
     * @param applicationMetadata The application metadata JSON node
     * @param requestHeaders The request headers JSON node
     * @param index The task index
     * @return DocumentUploadTask
     */
    private DocumentUploadTask convertToTask(JsonNode documentNode, JsonNode applicationMetadata,
                                             JsonNode requestHeaders, int index) {
        DocumentUploadTask task = new DocumentUploadTask();
        task.setManifestIndex(index);

        // Generate document ID
        String docId = UUID.randomUUID().toString();
        task.setDocumentId(docId);

        // Extract document path
        JsonNode documentPathNode = documentNode.get("documentPath");
        if (documentPathNode != null && !documentPathNode.isNull()) {
            task.setFilePath(documentPathNode.asText());
        }

        // Extract document metadata
        JsonNode documentMetadata = documentNode.get("metadata");
        task.setMetadata(documentMetadata);

        // Store application metadata
        task.setApplicationMetadata(applicationMetadata);

        // Extract headers from requestHeaders
        if (requestHeaders != null && requestHeaders.isObject()) {
            Iterator<String> fieldNames = requestHeaders.fieldNames();
            while (fieldNames.hasNext()) {
                String headerName = fieldNames.next();
                String headerValue = requestHeaders.get(headerName).asText();
                task.setHeader(headerName, headerValue);
            }
        }

        return task;
    }
}
