package com.bulkupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.bulkupload.config.BulkUploadProperties;

/**
 * Main entry point for the Bulk Upload Service.
 * 
 * This service provides automated document upload capabilities:
 * - Reads document manifest files (multiple formats supported)
 * - Uploads documents to configured API endpoint
 * - Handles retries with exponential backoff
 * - Generates detailed execution reports
 */
@SpringBootApplication
@EnableConfigurationProperties(BulkUploadProperties.class)
public class BulkUploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulkUploadServiceApplication.class, args);
    }
}
