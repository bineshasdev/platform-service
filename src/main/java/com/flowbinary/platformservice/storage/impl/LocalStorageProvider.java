package com.flowbinary.platformservice.storage.impl;

import com.flowbinary.platformservice.config.StorageProperties;
import com.flowbinary.platformservice.exception.StorageException;
import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import com.flowbinary.platformservice.storage.StorageObject;
import com.flowbinary.platformservice.storage.StorageProvider;
import com.flowbinary.platformservice.storage.StorageUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores files on the local filesystem.
 *
 * <p>Base path resolution order:
 * <ol>
 *   <li>Tenant credential {@code basePath} from {@code TenantStorageConfig.credentials}</li>
 *   <li>Platform default {@code app.storage.local-base-path} + tenant slug subdirectory</li>
 * </ol>
 *
 * <p>Files are written to: {@code <basePath>/<fileType>/<uuid>_<originalName>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStorageProvider implements StorageProvider {

    private final StorageProperties storageProperties;

    @Override
    public StorageBackend backend() {
        return StorageBackend.LOCAL;
    }

    @Override
    public StorageObject store(StorageUploadRequest request) {
        Path targetDir = resolveDirectory(request);
        ensureDirectory(targetDir);

        String filename   = UUID.randomUUID() + "_" + sanitize(request.getOriginalName());
        Path   targetPath = targetDir.resolve(filename);

        try (InputStream in = request.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("LOCAL: stored {} bytes → {}", request.getSizeBytes(), targetPath);
        } catch (IOException ex) {
            throw new StorageException("Failed to write file to local storage: " + targetPath, ex);
        }

        return StorageObject.builder()
                .storageKey(targetPath.toAbsolutePath().toString())
                .build();
    }

    @Override
    public StorageObject retrieve(TenantStorageConfig config, String storageKey,
                                  String originalName, String contentType) {
        Path path = Paths.get(storageKey);
        if (!Files.exists(path)) {
            throw new StorageException("File not found on local storage: " + storageKey);
        }
        try {
            return StorageObject.builder()
                    .storageKey(storageKey)
                    .data(Files.newInputStream(path))
                    .contentType(contentType)
                    .originalName(originalName)
                    .build();
        } catch (IOException ex) {
            throw new StorageException("Failed to read file from local storage: " + storageKey, ex);
        }
    }

    @Override
    public void delete(TenantStorageConfig config, String storageKey) {
        try {
            Path path = Paths.get(storageKey);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.debug("LOCAL: deleted {}", storageKey);
            }
        } catch (IOException ex) {
            throw new StorageException("Failed to delete file from local storage: " + storageKey, ex);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Path resolveDirectory(StorageUploadRequest request) {
        Map<String, Object> creds = request.getConfig().getCredentials();
        String basePath = creds.containsKey("basePath")
                ? (String) creds.get("basePath")
                : storageProperties.getLocalBasePath() + "/" + request.getTenantSlug();
        return Paths.get(basePath, request.getFileType().name().toLowerCase());
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new StorageException("Cannot create storage directory: " + dir, ex);
        }
    }

    /** Strip path separators and non-ASCII characters from user-supplied filenames. */
    private static String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
