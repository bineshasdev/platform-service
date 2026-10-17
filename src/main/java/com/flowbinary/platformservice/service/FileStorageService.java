package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.StorageProperties;
import com.flowbinary.platformservice.exception.FileTypeNotAllowedException;
import com.flowbinary.platformservice.exception.QuotaExceededException;
import com.flowbinary.platformservice.exception.StorageException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.TenantFileRepository;
import com.flowbinary.platformservice.repository.TenantStorageConfigRepository;
import com.flowbinary.platformservice.storage.StorageObject;
import com.flowbinary.platformservice.storage.StorageProvider;
import com.flowbinary.platformservice.storage.StorageProviderRegistry;
import com.flowbinary.platformservice.storage.StorageUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";

    private final TenantFileRepository          fileRepository;
    private final TenantStorageConfigRepository configRepository;
    private final StorageProviderRegistry       registry;
    private final StorageProperties             storageProperties;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public TenantFile upload(Tenant tenant, UUID uploadedBy,
                             FileType fileType, MultipartFile multipart) {
        validate(fileType, multipart);

        TenantStorageConfig config = getOrCreateConfig(tenant);
        checkQuota(config, multipart.getSize());

        StorageProvider provider = registry.get(config.getBackend());

        StorageUploadRequest uploadReq;
        try {
            uploadReq = StorageUploadRequest.builder()
                    .config(config)
                    .fileType(fileType)
                    .inputStream(multipart.getInputStream())
                    .contentType(multipart.getContentType())
                    .originalName(multipart.getOriginalFilename())
                    .sizeBytes(multipart.getSize())
                    .tenantSlug(tenant.getSlug())
                    .build();
        } catch (IOException ex) {
            throw new StorageException("Cannot read uploaded file stream", ex);
        }

        StorageObject stored = provider.store(uploadReq);

        // Persist metadata
        TenantFile file = TenantFile.builder()
                .tenant(tenant)
                .fileType(fileType)
                .originalName(multipart.getOriginalFilename())
                .contentType(multipart.getContentType())
                .sizeBytes(multipart.getSize())
                .backend(config.getBackend())
                .storageKey(stored.getStorageKey())
                .publicUrl(stored.getPublicUrl())
                .uploadedBy(uploadedBy)
                .status(ACTIVE)
                .build();

        file = fileRepository.save(file);

        // Track quota usage
        configRepository.incrementUsedBytes(tenant.getId(), multipart.getSize());

        log.info("File uploaded: id={} type={} size={} backend={} tenantId={}",
                file.getId(), fileType, multipart.getSize(), config.getBackend(), tenant.getId());
        return file;
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StorageObject download(Tenant tenant, UUID fileId) {
        TenantFile file = requireFile(tenant.getId(), fileId);
        TenantStorageConfig config = getOrCreateConfig(tenant);
        StorageProvider provider = registry.get(file.getBackend());
        return provider.retrieve(config, file.getStorageKey(), file.getOriginalName(), file.getContentType());
    }

    // ── Pre-signed URL ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String presignedUrl(Tenant tenant, UUID fileId) {
        TenantFile file = requireFile(tenant.getId(), fileId);
        if (file.getPublicUrl() != null) return file.getPublicUrl();

        TenantStorageConfig config = getOrCreateConfig(tenant);
        StorageProvider provider = registry.get(file.getBackend());
        Duration ttl = Duration.ofSeconds(storageProperties.getPresignedUrlTtlSeconds());
        return provider.presignedUrl(config, file.getStorageKey(), ttl);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TenantFile> listFiles(Tenant tenant, FileType fileType,
                                      int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (fileType != null) {
            return fileRepository.findByTenantIdAndFileTypeAndStatus(tenant.getId(), fileType, ACTIVE, pageable);
        }
        return fileRepository.findByTenantIdAndStatus(tenant.getId(), ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public TenantFile getMetadata(Tenant tenant, UUID fileId) {
        return requireFile(tenant.getId(), fileId);
    }

    /** Returns the most recently uploaded active file of a given type (e.g. current logo). */
    @Transactional(readOnly = true)
    public TenantFile getLatest(Tenant tenant, FileType fileType) {
        return fileRepository
                .findFirstByTenantIdAndFileTypeAndStatusOrderByCreatedAtDesc(tenant.getId(), fileType, ACTIVE)
                .orElseThrow(() -> new NoSuchElementException(
                        "No active " + fileType + " found for tenant " + tenant.getId()));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Tenant tenant, UUID fileId) {
        TenantFile file = requireFile(tenant.getId(), fileId);
        TenantStorageConfig config = getOrCreateConfig(tenant);

        // Attempt physical deletion; log but do not fail on backend errors
        try {
            registry.get(file.getBackend()).delete(config, file.getStorageKey());
        } catch (StorageException ex) {
            log.warn("Failed to delete physical file {}: {}", file.getStorageKey(), ex.getMessage());
        }

        file.setStatus(DELETED);
        fileRepository.save(file);

        configRepository.decrementUsedBytes(tenant.getId(), file.getSizeBytes());
        log.info("File deleted: id={} tenantId={}", fileId, tenant.getId());
    }

    // ── Storage config management ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TenantStorageConfig getConfig(Tenant tenant) {
        return getOrCreateConfig(tenant);
    }

    @Transactional
    public TenantStorageConfig updateConfig(Tenant tenant, StorageBackend backend,
                                            java.util.Map<String, Object> credentials,
                                            Long quotaMb) {
        TenantStorageConfig config = getOrCreateConfig(tenant);
        if (backend     != null) config.setBackend(backend);
        if (credentials != null) config.setCredentials(credentials);
        if (quotaMb     != null) config.setQuotaMb(quotaMb);
        return configRepository.save(config);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TenantFile requireFile(UUID tenantId, UUID fileId) {
        return fileRepository.findByIdAndTenantId(fileId, tenantId)
                .filter(f -> ACTIVE.equals(f.getStatus()))
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));
    }

    private TenantStorageConfig getOrCreateConfig(Tenant tenant) {
        return configRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            StorageBackend defaultBackend =
                    StorageBackend.valueOf(storageProperties.getDefaultBackend().toUpperCase());
            TenantStorageConfig cfg = TenantStorageConfig.builder()
                    .tenant(tenant)
                    .backend(defaultBackend)
                    .quotaMb(storageProperties.getDefaultQuotaMb())
                    .build();
            return configRepository.save(cfg);
        });
    }

    private void checkQuota(TenantStorageConfig config, long incomingBytes) {
        long quotaMb    = config.getQuotaMb() > 0
                ? config.getQuotaMb()
                : storageProperties.getDefaultQuotaMb();
        long quotaBytes = quotaMb * 1024L * 1024L;
        long projected  = config.getUsedBytes() + incomingBytes;
        if (projected > quotaBytes) {
            throw new QuotaExceededException(projected, quotaBytes);
        }
    }

    private static void validate(FileType fileType, MultipartFile file) {
        String mimeType = file.getContentType();
        if (!fileType.accepts(mimeType)) {
            throw new FileTypeNotAllowedException(mimeType, fileType);
        }
        if (file.getSize() > fileType.maxSizeBytes()) {
            throw new FileTypeNotAllowedException(file.getSize(), fileType.maxSizeBytes(), fileType);
        }
    }
}
