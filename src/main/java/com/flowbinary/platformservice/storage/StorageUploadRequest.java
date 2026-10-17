package com.flowbinary.platformservice.storage;

import com.flowbinary.platformservice.model.FileType;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;

/**
 * Internal transfer object passed from {@code FileStorageService} to a {@code StorageProvider}.
 */
@Getter
@Builder
public class StorageUploadRequest {

    /** Tenant-scoped storage configuration (contains credentials, base path, etc.). */
    private final TenantStorageConfig config;

    /** Logical file type (LOGO, PROFILE_IMAGE, …). */
    private final FileType fileType;

    /** Raw byte stream of the file content. */
    private final InputStream inputStream;

    /** Declared MIME type. */
    private final String contentType;

    /** Original filename from the multipart upload. */
    private final String originalName;

    /** File size in bytes. */
    private final long sizeBytes;

    /**
     * Tenant slug used for namespace isolation in storage paths
     * (e.g. S3 prefix, local subdirectory).
     */
    private final String tenantSlug;
}
