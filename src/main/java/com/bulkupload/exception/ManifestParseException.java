package com.bulkupload.exception;

/**
 * Exception thrown when manifest parsing fails.
 * 
 * This includes:
 * - File not found
 * - Invalid JSON syntax
 * - Unsupported manifest format
 * - Missing required fields
 */
public class ManifestParseException extends BulkUploadException {

    private static final String ERROR_CODE_PREFIX = "BULK-1";
    
    public static final String FILE_NOT_FOUND = ERROR_CODE_PREFIX + "001";
    public static final String INVALID_JSON = ERROR_CODE_PREFIX + "002";
    public static final String UNSUPPORTED_FORMAT = ERROR_CODE_PREFIX + "003";
    public static final String MISSING_REQUIRED_FIELD = ERROR_CODE_PREFIX + "004";
    public static final String MERGE_ERROR = ERROR_CODE_PREFIX + "005";
    public static final String IO_ERROR = ERROR_CODE_PREFIX + "006";

    private final String manifestPath;

    public ManifestParseException(String errorCode, String message, String manifestPath) {
        super(errorCode, message, manifestPath);
        this.manifestPath = manifestPath;
    }

    public ManifestParseException(String errorCode, String message, String manifestPath, Throwable cause) {
        super(errorCode, message, manifestPath, cause);
        this.manifestPath = manifestPath;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    // ============================================================
    // FACTORY METHODS
    // ============================================================

    public static ManifestParseException fileNotFound(String path) {
        return new ManifestParseException(
            FILE_NOT_FOUND,
            "Manifest file not found: " + path,
            path
        );
    }

    public static ManifestParseException invalidJson(String path, Throwable cause) {
        return new ManifestParseException(
            INVALID_JSON,
            "Failed to parse manifest as JSON: " + cause.getMessage(),
            path,
            cause
        );
    }

    public static ManifestParseException unsupportedFormat(String path, String details) {
        return new ManifestParseException(
            UNSUPPORTED_FORMAT,
            "Unsupported manifest format: " + details,
            path
        );
    }

    public static ManifestParseException missingRequiredField(String path, String fieldName) {
        return new ManifestParseException(
            MISSING_REQUIRED_FIELD,
            "Missing required field '" + fieldName + "' in manifest",
            path
        );
    }

    public static ManifestParseException mergeError(String primaryPath, String secondaryPath, String details) {
        return new ManifestParseException(
            MERGE_ERROR,
            "Failed to merge manifests: " + details,
            primaryPath + " + " + secondaryPath
        );
    }

    public static ManifestParseException ioError(String path, Throwable cause) {
        return new ManifestParseException(
            IO_ERROR,
            "IO error reading manifest: " + cause.getMessage(),
            path,
            cause
        );
    }
}
