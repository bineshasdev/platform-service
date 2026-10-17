package com.flowbinary.platformservice.dto.storage;

import com.flowbinary.platformservice.model.StorageBackend;
import jakarta.validation.constraints.Min;
import lombok.Getter;

import java.util.Map;

@Getter
public class StorageConfigRequest {

    private StorageBackend backend;

    /**
     * Backend credentials. Shape depends on the backend:
     * <ul>
     *   <li>S3: accessKeyId, secretAccessKey, bucket, region, prefix?, endpoint?</li>
     *   <li>LOCAL: basePath (optional)</li>
     *   <li>DATABASE: empty</li>
     * </ul>
     */
    private Map<String, Object> credentials;

    /**
     * Quota in megabytes. Must be ≥ 1 if provided. Pass 0 to reset to platform default.
     */
    @Min(value = 0, message = "quotaMb must be 0 (platform default) or a positive number")
    private Long quotaMb;
}
