# Bulk Upload Service

A robust, flexible document upload utility for batch processing. Supports multiple manifest formats, retry with exponential backoff, and detailed reporting.

## Features

- **Flexible Manifest Formats**: Supports multiple JSON structures for defining uploads
- **Split Manifest Support**: Separate metadata and file locations into different files
- **Retry with Exponential Backoff**: Configurable retry logic for transient failures
- **Detailed Reporting**: JSON or CSV reports with per-document results
- **Configurable Behavior**: Continue on error, skip missing files, pre-validation
- **RFC 7807 Error Handling**: Proper error responses with detailed information

## Quick Start

1. Configure `application.properties` with your manifest path and upload endpoint
2. Create your manifest JSON file
3. Start the service: `mvn spring-boot:run`
4. Trigger upload: `POST http://localhost:8080/api/v1/bulk-upload/run`

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/bulk-upload/run` | POST | Execute bulk upload |
| `/api/v1/bulk-upload/config` | GET | View current configuration |
| `/api/v1/bulk-upload/health` | GET | Health check |

## Supported Manifest Formats

### Format 1: Simple Array

The simplest format - an array of document entries.

```json
[
  {
    "filePath": "C:/documents/doc1.pdf",
    "metadata": {
      "documentName": "Invoice 2024",
      "conflictResolution": "KEEP_BOTH",
      "documentType": {
        "id": "Invoice",
        "attributes": [
          { "attributeName": "InvoiceNumber", "value": "INV-001" }
        ]
      }
    }
  },
  {
    "filePath": "C:/documents/doc2.pdf",
    "metadata": {
      "documentName": "Contract",
      "documentType": { "id": "Contract" }
    }
  }
]
```

### Format 2: Documents with Defaults

Reduces repetition by defining default values.

```json
{
  "defaults": {
    "X-Tenant-Id": "tenant-123",
    "X-Workspace-Id": "workspace-456",
    "metadata": {
      "conflictResolution": "KEEP_BOTH"
    }
  },
  "documents": [
    {
      "filePath": "C:/documents/doc1.pdf",
      "metadata": {
        "documentName": "Invoice 2024",
        "documentType": { "id": "Invoice" }
      }
    },
    {
      "filePath": "C:/documents/doc2.pdf",
      "metadata": {
        "documentName": "Contract",
        "documentType": { "id": "Contract" }
      }
    }
  ]
}
```

### Format 3: Batched Format

Organize documents into batches with batch-specific defaults.

```json
{
  "defaults": {
    "X-Tenant-Id": "tenant-123"
  },
  "batches": [
    {
      "defaults": {
        "X-Workspace-Id": "workspace-invoices",
        "metadata": { "documentType": { "id": "Invoice" } }
      },
      "documents": [
        { "filePath": "C:/invoices/inv1.pdf", "metadata": { "documentName": "Invoice 1" } },
        { "filePath": "C:/invoices/inv2.pdf", "metadata": { "documentName": "Invoice 2" } }
      ]
    },
    {
      "defaults": {
        "X-Workspace-Id": "workspace-contracts",
        "metadata": { "documentType": { "id": "Contract" } }
      },
      "documents": [
        { "filePath": "C:/contracts/con1.pdf", "metadata": { "documentName": "Contract 1" } }
      ]
    }
  ]
}
```

### Format 4: Split Manifests (Metadata + Locations)

Keep metadata and file locations in separate files.

**Primary manifest (metadata.json):**
```json
{
  "documents": [
    {
      "documentId": "doc-001",
      "metadata": {
        "documentName": "Invoice 2024",
        "documentType": { "id": "Invoice" }
      }
    },
    {
      "documentId": "doc-002",
      "metadata": {
        "documentName": "Contract",
        "documentType": { "id": "Contract" }
      }
    }
  ]
}
```

**Secondary manifest (locations.json):**
```json
{
  "locations": [
    { "documentId": "doc-001", "filePath": "C:/documents/doc1.pdf" },
    { "documentId": "doc-002", "filePath": "C:/documents/doc2.pdf" }
  ]
}
```

**Configuration:**
```properties
bulk.manifest-path=C:/manifests/metadata.json
bulk.secondary-manifest-path=C:/manifests/locations.json
bulk.manifest-correlation-key=documentId
```

### Format 5: Simple File List

Minimal format for simple file uploads.

```json
{
  "files": [
    { "path": "C:/documents/doc1.pdf", "name": "Document 1" },
    { "path": "C:/documents/doc2.pdf", "name": "Document 2" }
  ]
}
```

## Configuration Reference

### Manifest Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `bulk.manifest-path` | Path to primary manifest file | Required |
| `bulk.secondary-manifest-path` | Path to secondary manifest (optional) | - |
| `bulk.manifest-correlation-key` | Key for correlating split manifests | `documentId` |

### Upload Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `bulk.upload-endpoint` | Target API endpoint URL | Required |
| `bulk.connection-timeout-ms` | Connection timeout | `30000` |
| `bulk.read-timeout-ms` | Read timeout | `60000` |
| `bulk.max-file-size-bytes` | Maximum file size | `104857600` (100MB) |

### Default Headers

| Property | Description |
|----------|-------------|
| `bulk.default-headers.user-id` | Default X-User-Id header |
| `bulk.default-headers.tenant-id` | Default X-Tenant-Id header |
| `bulk.default-headers.workspace-id` | Default X-Workspace-Id header |

### Retry Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `bulk.retry-count` | Number of retry attempts | `3` |
| `bulk.retry-delay-ms` | Initial retry delay | `1000` |
| `bulk.retry-backoff-multiplier` | Exponential backoff multiplier | `2.0` |

### Behavior Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `bulk.skip-missing-files` | Continue if file is missing | `true` |
| `bulk.continue-on-error` | Continue after upload failure | `true` |
| `bulk.pre-validate-manifest` | Validate all files before starting | `true` |

### Report Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `bulk.report-dir` | Output directory for reports | Required |
| `bulk.report-format` | Report format (JSON or CSV) | `JSON` |

## Report Structure

### JSON Report Example

```json
{
  "metadata": {
    "executionId": "exec-a1b2c3d4",
    "status": "COMPLETED_WITH_ERRORS",
    "startedAt": "2024-01-15T10:30:00Z",
    "finishedAt": "2024-01-15T10:32:45Z",
    "durationMs": 165000,
    "durationFormatted": "2m 45s",
    "manifestPath": "C:/manifests/sample.json"
  },
  "summary": {
    "totalDocuments": 10,
    "successful": 8,
    "failed": 1,
    "skipped": 1,
    "errors": 0,
    "successRate": "80.00%"
  },
  "results": [
    {
      "index": 0,
      "documentId": "doc-001",
      "filePath": "C:/documents/doc1.pdf",
      "status": "SUCCESS",
      "httpStatus": 201,
      "durationMs": 1250,
      "attempts": 1
    },
    {
      "index": 1,
      "documentId": "doc-002",
      "filePath": "C:/documents/doc2.pdf",
      "status": "FAILED",
      "httpStatus": 400,
      "durationMs": 350,
      "attempts": 1,
      "errorMessage": "HTTP 400: Invalid document type",
      "apiError": {
        "code": "DOC-1001",
        "message": "Invalid document type specified"
      }
    }
  ],
  "failures": [
    {
      "index": 1,
      "filePath": "C:/documents/doc2.pdf",
      "error": "HTTP 400: Invalid document type",
      "httpStatus": 400
    }
  ]
}
```

## Error Handling

The service uses RFC 7807 compliant error responses:

```json
{
  "type": "https://teamsync.example.com/errors/bulk-upload/manifest-parse-error",
  "title": "Manifest Parse Error",
  "status": 400,
  "detail": "Failed to parse manifest as JSON: Unexpected character at position 42",
  "errorCode": "BULK-1002",
  "timestamp": "2024-01-15T10:30:00Z",
  "context": "C:/manifests/invalid.json"
}
```

### Error Codes

| Code | Description |
|------|-------------|
| `BULK-1001` | Manifest file not found |
| `BULK-1002` | Invalid JSON syntax |
| `BULK-1003` | Unsupported manifest format |
| `BULK-1004` | Missing required field |
| `BULK-1005` | Manifest merge error |
| `BULK-2001` | HTTP error from upload API |
| `BULK-2002` | Connection error |
| `BULK-2003` | Timeout error |
| `BULK-2004` | File read error |
| `BULK-2006` | File too large |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    BulkUploadController                         │
│                   (REST API endpoints)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     BulkUploadService                           │
│              (Orchestration & coordination)                     │
└─────────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
┌──────────────────┐ ┌───────────────────┐ ┌──────────────────────┐
│ ManifestParser   │ │ DocumentUpload    │ │ ReportGenerator      │
│ Service          │ │ Client            │ │ Service              │
├──────────────────┤ ├───────────────────┤ ├──────────────────────┤
│ - Parse JSON     │ │ - Build requests  │ │ - Generate JSON/CSV  │
│ - Merge formats  │ │ - Handle retries  │ │ - Calculate stats    │
│ - Validate files │ │ - Parse errors    │ │ - Format summaries   │
└──────────────────┘ └───────────────────┘ └──────────────────────┘
```

## Building & Running

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Run with custom config
java -jar target/bulk-upload-service-1.0.0-SNAPSHOT.jar \
  --bulk.manifest-path=/path/to/manifest.json
```

## Extending the Service

### Adding New Manifest Formats

1. Add detection logic in `ManifestParserService.extractDocumentNodes()`
2. Implement extraction method for the new format
3. Update field name arrays if new field names are used

### Adding New Report Formats

1. Add new enum value to `BulkUploadProperties.ReportFormat`
2. Implement generation method in `ReportGeneratorService`
3. Add format selection in `generateReport()`

### Customizing Retry Logic

Modify `BulkUploadService.isRetryable()` to change which errors trigger retries.

## Troubleshooting

### Common Issues

**Manifest not found**
- Check the path in `bulk.manifest-path`
- Ensure backslashes are escaped in Windows paths

**Upload fails with 401/403**
- Verify `bulk.default-headers.*` are correctly set
- Check if per-document header overrides are needed

**Timeout errors**
- Increase `bulk.read-timeout-ms`
- Check network connectivity to upload endpoint

### Enabling Debug Logging

```properties
logging.level.com.bulkupload=DEBUG
logging.level.org.apache.http=DEBUG
```
"# bulk-upload" 
