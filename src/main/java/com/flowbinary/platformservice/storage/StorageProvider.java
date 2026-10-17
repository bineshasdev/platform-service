package com.flowbinary.platformservice.storage;

import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantStorageConfig;

import java.time.Duration;

/**
 * Pluggable file storage abstraction.
 *
 * <p>Register implementations as Spring beans; the {@link StorageProviderRegistry}
 * picks the correct one for each tenant based on their {@link TenantStorageConfig#getBackend()}.
 *
 * <p>All methods must be idempotent and should throw {@link com.flowbinary.platformservice.exception.StorageException}
 * (or a subclass) on unrecoverable failures.
 */
public interface StorageProvider {

    /** The backend type this provider implements. */
    StorageBackend backend();

    /**
     * Store the uploaded bytes and return a result containing the opaque storage key
     * (and optional public URL).
     */
    StorageObject store(StorageUploadRequest request);

    /**
     * Retrieve file bytes by their storage key.
     *
     * @throws com.flowbinary.platformservice.exception.StorageException if the object is not found
     */
    StorageObject retrieve(TenantStorageConfig config, String storageKey, String originalName, String contentType);

    /**
     * Delete the object identified by {@code storageKey}.
     * Implementations should treat "not found" as a no-op (idempotent delete).
     */
    void delete(TenantStorageConfig config, String storageKey);

    /**
     * Generate a time-limited, pre-signed URL for direct download.
     *
     * <p>LOCAL and DATABASE backends may fall back to the in-app download endpoint
     * by returning {@code null}; callers must handle that case.
     *
     * @param ttl  how long the URL should remain valid
     * @return pre-signed URL, or {@code null} if not supported by this backend
     */
    default String presignedUrl(TenantStorageConfig config, String storageKey, Duration ttl) {
        return null;
    }
}
