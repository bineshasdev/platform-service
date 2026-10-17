package com.flowbinary.platformservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Platform-level storage defaults bound from {@code app.storage.*}.
 *
 * <p>Individual tenants can override these values via their {@code TenantStorageConfig}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Default storage backend for tenants that have no {@code TenantStorageConfig} row yet.
     * Values: LOCAL | S3 | DATABASE
     */
    private String defaultBackend = "LOCAL";

    /**
     * Default storage quota in megabytes applied when a tenant's {@code quotaMb} is 0.
     * Defaults to 512 MB.
     */
    private long defaultQuotaMb = 512L;

    /**
     * Root directory for the LOCAL backend.
     * A tenant-slug subdirectory is appended automatically:
     * {@code <localBasePath>/<tenantSlug>/<fileType>/}.
     */
    private String localBasePath = "/tmp/flowcore/uploads";

    /**
     * Maximum multipart file size enforced at the Spring layer (before quota checks).
     * Should align with {@code spring.servlet.multipart.max-file-size}.
     */
    private String maxFileSize = "50MB";

    /**
     * Default TTL (in seconds) for S3 pre-signed URLs.
     * Defaults to 1 hour.
     */
    private long presignedUrlTtlSeconds = 3600L;

    /**
     * When true, the public URL is returned in upload responses for S3 objects
     * stored in a public bucket. When false, pre-signed URLs are used instead.
     */
    private boolean s3PublicBucket = false;
}
