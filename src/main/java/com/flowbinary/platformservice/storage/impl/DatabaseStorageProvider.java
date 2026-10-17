package com.flowbinary.platformservice.storage.impl;

import com.flowbinary.platformservice.exception.StorageException;
import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import com.flowbinary.platformservice.storage.StorageObject;
import com.flowbinary.platformservice.storage.StorageProvider;
import com.flowbinary.platformservice.storage.StorageUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Stores raw file bytes inline in the {@code file_data} PostgreSQL table (BYTEA column).
 *
 * <p>Best suited for small files (thumbnails, icons) or air-gapped environments
 * where no external object store is available.
 *
 * <p>The {@code storageKey} is the UUID of the {@code file_data} row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseStorageProvider implements StorageProvider {

    private final JdbcTemplate jdbc;

    @Override
    public StorageBackend backend() {
        return StorageBackend.DATABASE;
    }

    @Override
    public StorageObject store(StorageUploadRequest request) {
        String rowId = UUID.randomUUID().toString();
        byte[] bytes = readBytes(request);

        jdbc.update(
            "INSERT INTO file_data (id, file_id, data) VALUES (?::uuid, ?::uuid, ?)",
            rowId,
            rowId,   // file_id will be updated by FileStorageService after TenantFile is persisted
            bytes
        );

        log.debug("DATABASE: stored {} bytes, row id={}", bytes.length, rowId);
        return StorageObject.builder()
                .storageKey(rowId)
                .build();
    }

    @Override
    public StorageObject retrieve(TenantStorageConfig config, String storageKey,
                                  String originalName, String contentType) {
        byte[] data = jdbc.query(
            "SELECT data FROM file_data WHERE id = ?::uuid",
            rs -> {
                if (!rs.next()) return null;
                return rs.getBytes("data");
            },
            storageKey
        );

        if (data == null) {
            throw new StorageException("File not found in database storage: " + storageKey);
        }

        return StorageObject.builder()
                .storageKey(storageKey)
                .data(new ByteArrayInputStream(data))
                .contentType(contentType)
                .originalName(originalName)
                .build();
    }

    @Override
    public void delete(TenantStorageConfig config, String storageKey) {
        int rows = jdbc.update("DELETE FROM file_data WHERE id = ?::uuid", storageKey);
        log.debug("DATABASE: deleted {} row(s) for key={}", rows, storageKey);
    }

    /** Pre-signed URLs are not applicable for database-backed storage. */
    @Override
    public String presignedUrl(TenantStorageConfig config, String storageKey, Duration ttl) {
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] readBytes(StorageUploadRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new StorageException("Failed to read upload stream", ex);
        }
    }
}
