package com.flowbinary.platformservice.dto.storage;

import com.flowbinary.platformservice.model.StorageBackend;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StorageConfigResponse {

    private final UUID           id;
    private final StorageBackend backend;
    private final long           quotaMb;
    private final long           usedBytes;
    private final long           availableBytes;
    private final double         usedPercent;

    /**
     * Credentials are intentionally omitted from the response to avoid
     * leaking secrets (e.g. S3 secret access keys) in API responses.
     */

    public static StorageConfigResponse from(TenantStorageConfig config, long defaultQuotaMb) {
        long effectiveQuotaMb = config.getQuotaMb() > 0 ? config.getQuotaMb() : defaultQuotaMb;
        long quotaBytes       = effectiveQuotaMb * 1024L * 1024L;
        long available        = Math.max(0, quotaBytes - config.getUsedBytes());
        double usedPct        = quotaBytes == 0 ? 0.0
                : Math.min(100.0, (config.getUsedBytes() * 100.0) / quotaBytes);

        return StorageConfigResponse.builder()
                .id(config.getId())
                .backend(config.getBackend())
                .quotaMb(effectiveQuotaMb)
                .usedBytes(config.getUsedBytes())
                .availableBytes(available)
                .usedPercent(Math.round(usedPct * 10.0) / 10.0)
                .build();
    }
}
