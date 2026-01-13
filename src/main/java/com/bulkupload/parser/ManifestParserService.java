package com.bulkupload.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bulkupload.config.BulkUploadProperties;
import com.bulkupload.dto.DocumentUploadTask;
import com.bulkupload.exception.ManifestParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Service responsible for parsing manifest files into DocumentUploadTask objects.
 * 
 * Supports multiple manifest formats:
 * 
 * 1. SIMPLE ARRAY FORMAT:
 *    [{ "filePath": "...", "metadata": {...} }, ...]
 * 
 * 2. DOCUMENTS WITH DEFAULTS:
 *    { "defaults": {...}, "documents": [{...}, ...] }
 * 
 * 3. BATCHED FORMAT:
 *    { "defaults": {...}, "batches": [{ "defaults": {...}, "documents": [...] }, ...] }
 * 
 * 4. SPLIT MANIFESTS (metadata + file locations in separate files):
 *    Primary: { "documents": [{ "documentId": "doc1", "metadata": {...} }, ...] }
 *    Secondary: { "locations": [{ "documentId": "doc1", "filePath": "..." }, ...] }
 * 
 * 5. FLAT FILE LOCATIONS:
 *    { "files": [{ "path": "...", "name": "..." }, ...] }
 */
@Service
public class ManifestParserService {

    private static final Logger log = LoggerFactory.getLogger(ManifestParserService.class);

    // Known field names for file paths across different manifest formats
    private static final String[] FILE_PATH_FIELDS = {
        "filePath", "documentPath", "path", "file", "location", "sourcePath"
    };

    // Known field names for metadata
    private static final String[] METADATA_FIELDS = {
        "metadata", "meta", "documentMetadata", "properties", "attributes"
    };

    // Known field names for document ID (used for correlation)
    private static final String[] ID_FIELDS = {
        "documentId", "id", "docId", "fileId", "correlationId", "key"
    };

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
     * Parses the configured manifest file(s) into a list of DocumentUploadTask.
     * 
     * @return List of parsed upload tasks
     * @throws ManifestParseException if parsing fails
     */
    public List<DocumentUploadTask> parseManifest() {
        log.info("Starting manifest parsing from: {}", properties.getManifestPath());
        
        // Parse primary manifest
        JsonNode primaryRoot = readManifestFile(properties.getManifestPath());
        
        // Parse secondary manifest if configured
        JsonNode secondaryRoot = null;
        if (properties.getSecondaryManifestPath() != null && !properties.getSecondaryManifestPath().isBlank()) {
            log.info("Secondary manifest configured: {}", properties.getSecondaryManifestPath());
            secondaryRoot = readManifestFile(properties.getSecondaryManifestPath());
        }
        
        // Extract document nodes based on detected format
        List<JsonNode> documentNodes = extractDocumentNodes(primaryRoot);
        log.info("Extracted {} document entries from primary manifest", documentNodes.size());
        
        // Merge with secondary manifest if present
        if (secondaryRoot != null) {
            documentNodes = mergeWithSecondaryManifest(documentNodes, secondaryRoot);
            log.info("After merge: {} document entries", documentNodes.size());
        }
        
        // Convert to DocumentUploadTask objects
        List<DocumentUploadTask> tasks = new ArrayList<>();
        for (int i = 0; i < documentNodes.size(); i++) {
            DocumentUploadTask task = convertToTask(documentNodes.get(i), i);
            tasks.add(task);
        }
        
        log.info("Successfully parsed {} document upload tasks", tasks.size());
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
    // FORMAT DETECTION AND EXTRACTION
    // ============================================================

    /**
     * Extracts document nodes from the manifest, detecting format automatically.
     */
    private List<JsonNode> extractDocumentNodes(JsonNode root) {
        if (root == null) {
            return new ArrayList<>();
        }
        
        // Format 1: Simple array at root
        if (root.isArray()) {
            log.debug("Detected format: Simple array");
            return arrayToList(root);
        }
        
        if (!root.isObject()) {
            throw ManifestParseException.unsupportedFormat(
                properties.getManifestPath(), 
                "Root must be an array or object"
            );
        }
        
        // Format 3: Batched format
        if (root.has("batches")) {
            log.debug("Detected format: Batched");
            return extractFromBatchedFormat(root);
        }
        
        // Format 2: Documents with defaults
        if (root.has("documents")) {
            log.debug("Detected format: Documents with defaults");
            return extractFromDocumentsFormat(root);
        }
        
        // Format 5: Flat file locations
        if (root.has("files")) {
            log.debug("Detected format: Flat file locations");
            return extractFromFilesFormat(root);
        }
        
        // Fallback: treat the object itself as a single document
        log.debug("Detected format: Single document object");
        List<JsonNode> result = new ArrayList<>();
        result.add(root);
        return result;
    }

    /**
     * Extracts documents from { "documents": [...], "defaults": {...} } format.
     */
    private List<JsonNode> extractFromDocumentsFormat(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode defaults = root.get("defaults");
        JsonNode documents = root.get("documents");
        
        if (documents == null || !documents.isArray()) {
            throw ManifestParseException.unsupportedFormat(
                properties.getManifestPath(),
                "'documents' field must be an array"
            );
        }
        
        for (JsonNode doc : documents) {
            JsonNode merged = mergeDefaults(defaults, doc);
            result.add(merged);
        }
        
        return result;
    }

    /**
     * Extracts documents from batched format.
     */
    private List<JsonNode> extractFromBatchedFormat(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode globalDefaults = root.get("defaults");
        JsonNode batches = root.get("batches");
        
        if (batches == null || !batches.isArray()) {
            throw ManifestParseException.unsupportedFormat(
                properties.getManifestPath(),
                "'batches' field must be an array"
            );
        }
        
        for (JsonNode batch : batches) {
            JsonNode batchDefaults = batch.get("defaults");
            JsonNode documents = batch.get("documents");
            
            if (documents != null && documents.isArray()) {
                for (JsonNode doc : documents) {
                    // Apply defaults: global -> batch -> document
                    JsonNode merged = doc;
                    if (batchDefaults != null) {
                        merged = mergeDefaults(batchDefaults, merged);
                    }
                    if (globalDefaults != null) {
                        merged = mergeDefaults(globalDefaults, merged);
                    }
                    result.add(merged);
                }
            }
        }
        
        return result;
    }

    /**
     * Extracts from { "files": [...] } format.
     */
    private List<JsonNode> extractFromFilesFormat(JsonNode root) {
        JsonNode files = root.get("files");
        if (files == null || !files.isArray()) {
            throw ManifestParseException.unsupportedFormat(
                properties.getManifestPath(),
                "'files' field must be an array"
            );
        }
        return arrayToList(files);
    }

    // ============================================================
    // MANIFEST MERGING
    // ============================================================

    /**
     * Merges two manifests using the configured correlation key.
     * Primary manifest provides metadata, secondary provides file locations.
     */
    private List<JsonNode> mergeWithSecondaryManifest(List<JsonNode> primaryDocs, JsonNode secondaryRoot) {
        String correlationKey = properties.getManifestCorrelationKey();
        log.debug("Merging manifests using correlation key: {}", correlationKey);
        
        // Build lookup map from secondary manifest
        Map<String, JsonNode> locationMap = buildLocationMap(secondaryRoot, correlationKey);
        
        if (locationMap.isEmpty()) {
            log.warn("Secondary manifest contained no location mappings");
            return primaryDocs;
        }
        
        List<JsonNode> merged = new ArrayList<>();
        
        for (JsonNode primaryDoc : primaryDocs) {
            String docId = findFieldValue(primaryDoc, ID_FIELDS);
            
            if (docId != null && locationMap.containsKey(docId)) {
                // Merge location info into primary document
                JsonNode locationNode = locationMap.get(docId);
                JsonNode mergedDoc = deepMerge(locationNode, primaryDoc);
                merged.add(mergedDoc);
            } else {
                // Keep primary document as-is (may already have file path)
                merged.add(primaryDoc);
            }
        }
        
        return merged;
    }

    /**
     * Builds a map of document ID -> location node from secondary manifest.
     */
    private Map<String, JsonNode> buildLocationMap(JsonNode secondaryRoot, String correlationKey) {
        Map<String, JsonNode> map = new HashMap<>();
        
        // Try different structures for the secondary manifest
        JsonNode locations = secondaryRoot.get("locations");
        if (locations == null) {
            locations = secondaryRoot.get("files");
        }
        if (locations == null && secondaryRoot.isArray()) {
            locations = secondaryRoot;
        }
        
        if (locations == null || !locations.isArray()) {
            log.warn("Secondary manifest does not contain a valid locations/files array");
            return map;
        }
        
        for (JsonNode loc : locations) {
            String id = findFieldValue(loc, new String[]{correlationKey});
            if (id == null) {
                id = findFieldValue(loc, ID_FIELDS);
            }
            if (id != null) {
                map.put(id, loc);
            }
        }
        
        log.debug("Built location map with {} entries", map.size());
        return map;
    }

    // ============================================================
    // CONVERSION TO UPLOAD TASKS
    // ============================================================

    /**
     * Converts a JSON node to a DocumentUploadTask.
     */
    private DocumentUploadTask convertToTask(JsonNode node, int index) {
        DocumentUploadTask task = new DocumentUploadTask();
        task.setManifestIndex(index);
        
        // Extract document ID
        String docId = findFieldValue(node, ID_FIELDS);
        task.setDocumentId(docId != null ? docId : UUID.randomUUID().toString());
        
        // Extract file path
        String filePath = findFieldValue(node, FILE_PATH_FIELDS);
        task.setFilePath(filePath);
        
        // Extract metadata
        JsonNode metadata = findFieldNode(node, METADATA_FIELDS);
        if (metadata == null) {
            // If no explicit metadata field, use the whole node as metadata (minus file path)
            metadata = objectMapper.createObjectNode();
        }
        task.setMetadata(metadata);
        
        // Extract header overrides
        extractHeaderOverrides(node, task);
        
        return task;
    }

    /**
     * Extracts header overrides from the document node.
     */
    private void extractHeaderOverrides(JsonNode node, DocumentUploadTask task) {
        // Check for explicit headers section
        JsonNode headers = node.get("headers");
        if (headers != null && headers.isObject()) {
            Iterator<String> fieldNames = headers.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                task.setHeader(name, headers.get(name).asText());
            }
        }
        
        // Also check for individual header fields
        String[] headerFields = {"X-User-Id", "X-Tenant-Id", "X-Workspace-Id", "Cookie"};
        for (String field : headerFields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                task.setHeader(field, value.asText());
            }
        }
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    /**
     * Merges defaults into target node (target fields take precedence).
     */
    private JsonNode mergeDefaults(JsonNode defaults, JsonNode target) {
        if (defaults == null || !defaults.isObject()) {
            return target;
        }
        if (target == null || !target.isObject()) {
            return defaults;
        }
        
        ObjectNode result = objectMapper.createObjectNode();
        
        // First add all default fields
        defaults.fields().forEachRemaining(entry -> 
            result.set(entry.getKey(), entry.getValue().deepCopy()));
        
        // Then overlay target fields (overriding defaults)
        target.fields().forEachRemaining(entry -> 
            result.set(entry.getKey(), entry.getValue().deepCopy()));
        
        return result;
    }

    /**
     * Deep merges two nodes (source fields fill missing target fields).
     */
    private JsonNode deepMerge(JsonNode source, JsonNode target) {
        if (source == null) return target;
        if (target == null) return source;
        if (!source.isObject() || !target.isObject()) return target;
        
        ObjectNode result = objectMapper.createObjectNode();
        
        // Add all target fields first
        target.fields().forEachRemaining(entry -> 
            result.set(entry.getKey(), entry.getValue().deepCopy()));
        
        // Add missing fields from source
        source.fields().forEachRemaining(entry -> {
            if (!result.has(entry.getKey())) {
                result.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
        
        return result;
    }

    /**
     * Finds the first non-null value for any of the given field names.
     */
    private String findFieldValue(JsonNode node, String[] fieldNames) {
        if (node == null) return null;
        for (String field : fieldNames) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    /**
     * Finds the first non-null node for any of the given field names.
     */
    private JsonNode findFieldNode(JsonNode node, String[] fieldNames) {
        if (node == null) return null;
        for (String field : fieldNames) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Converts a JSON array to a List.
     */
    private List<JsonNode> arrayToList(JsonNode array) {
        List<JsonNode> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(result::add);
        }
        return result;
    }
}
