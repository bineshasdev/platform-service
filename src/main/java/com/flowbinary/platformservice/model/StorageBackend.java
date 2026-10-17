package com.flowbinary.platformservice.model;

/**
 * Storage backend implementations available for file storage.
 *
 * <ul>
 *   <li>{@link #LOCAL}    — files written to the local filesystem (dev / single-node)</li>
 *   <li>{@link #S3}       — files stored in an S3-compatible object store (AWS, MinIO, etc.)</li>
 *   <li>{@link #DATABASE} — raw bytes persisted in the {@code file_data} table (small files / air-gapped)</li>
 * </ul>
 */
public enum StorageBackend {
    LOCAL,
    S3,
    DATABASE
}
