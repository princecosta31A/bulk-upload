package com.bulkupload.exception;

/**
 * Base exception for all bulk upload related errors.
 * 
 * Provides common fields for error tracking and reporting.
 */
public class BulkUploadException extends RuntimeException {

    private final String errorCode;
    private final String context;

    public BulkUploadException(String message) {
        super(message);
        this.errorCode = "BULK-0000";
        this.context = null;
    }

    public BulkUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    public BulkUploadException(String errorCode, String message, String context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }

    public BulkUploadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    public BulkUploadException(String errorCode, String message, String context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getContext() {
        return context;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode).append("] ").append(getMessage());
        if (context != null) {
            sb.append(" (context: ").append(context).append(")");
        }
        return sb.toString();
    }
}
