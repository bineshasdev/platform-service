package com.flowbinary.platformservice.storage;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;

/**
 * Result returned by {@code StorageProvider.store()} and {@code StorageProvider.retrieve()}.
 */
@Getter
@Builder
public class StorageObject {

    /**
     * Opaque key that uniquely identifies the stored object within the backend.
     * Persisted in {@code TenantFile.storageKey}.
     */
    private final String storageKey;

    /**
     * Public URL if the backend makes files directly accessible (e.g. S3 public bucket,
     * local server with static file serving). Null when not applicable.
     */
    private final String publicUrl;

    /**
     * Stream of the file bytes — populated only by {@code retrieve()}, null otherwise.
     */
    private final InputStream data;

    /** MIME type, populated on retrieval. */
    private final String contentType;

    /** Original filename, populated on retrieval. */
    private final String originalName;
}
