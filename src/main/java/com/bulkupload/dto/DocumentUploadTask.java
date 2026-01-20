package com.bulkupload.dto;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single document upload task extracted from the manifest.
 *
 * This DTO encapsulates all information needed to upload one document:
 * - File location (resolved to actual File object)
 * - Metadata to send with the upload
 * - Per-document header overrides
 * - Correlation ID for tracking/linking with other manifests
 */
@Getter
@Setter
public class DocumentUploadTask {

    // ============================================================
    // IDENTIFICATION
    // ============================================================
    
    /**
     * Unique identifier for this task, used for:
     * - Correlating with secondary manifests
     * - Report generation
     * - Log tracing
     */
    private String documentId;
    
    /**
     * Index position in the original manifest (0-based).
     * Useful for report generation and debugging.
     */
    private int manifestIndex;

    // ============================================================
    // FILE INFORMATION
    // ============================================================
    
    /**
     * Original file path as specified in manifest.
     */
    private String filePath;
    
    /**
     * Resolved File object. Null if file doesn't exist.
     */
    private File resolvedFile;
    
    /**
     * Whether the file exists and is readable.
     */
    private boolean fileValid;
    
    /**
     * Validation error message if file is invalid.
     */
    private String fileValidationError;

    // ============================================================
    // METADATA
    // ============================================================

    /**
     * Raw metadata JSON node from manifest.
     * Preserved for flexibility - can contain any structure.
     */
    private JsonNode metadata;

    /**
     * Application metadata from the manifest.
     * Contains application-level information like applicationId.
     */
    private JsonNode applicationMetadata;

    // ============================================================
    // HEADER OVERRIDES
    // ============================================================
    
    /**
     * Per-document header overrides.
     * Keys are header names (e.g., "X-User-Id", "X-Tenant-Id").
     * Values override the default headers from configuration.
     */
    private Map<String, String> headerOverrides = new HashMap<>();

    // ============================================================
    // CONSTRUCTORS
    // ============================================================
    
    public DocumentUploadTask() {
    }
    
    public DocumentUploadTask(int manifestIndex, String filePath) {
        this.manifestIndex = manifestIndex;
        this.filePath = filePath;
    }

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================
    
    /**
     * Checks if this task is ready for upload.
     * A task is ready if the file is valid and metadata is present.
     */
    public boolean isReadyForUpload() {
        return fileValid && resolvedFile != null && resolvedFile.canRead();
    }
    
    /**
     * Gets a specific header value, returning null if not set.
     */
    public String getHeader(String headerName) {
        return headerOverrides.get(headerName);
    }
    
    /**
     * Sets a header override.
     */
    public void setHeader(String headerName, String value) {
        if (value != null) {
            headerOverrides.put(headerName, value);
        }
    }

    @Override
    public String toString() {
        return String.format("DocumentUploadTask[index=%d, id=%s, file=%s, valid=%s]",
                manifestIndex, documentId, filePath, fileValid);
    }
}
