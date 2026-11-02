package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platformservice.config.StorageProperties;
import com.flowbinary.platformservice.dto.storage.StorageConfigRequest;
import com.flowbinary.platformservice.dto.storage.StorageConfigResponse;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantStorageConfig;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/storage-config")
@RequiredArgsConstructor
@Tag(name = "Storage Config", description = "Manage per-tenant storage backend and quota")
public class StorageConfigController {

    private final FileStorageService    fileStorageService;
    private final TenantContextResolver tenantContextResolver;
    private final StorageProperties     storageProperties;

    @GetMapping
    @RequiresPrivilege("platform.storage.manage")
    @Operation(summary = "Get current tenant storage configuration and quota usage")
    StorageConfigResponse getConfig(Authentication authentication) {
        Tenant tenant             = tenantContextResolver.resolveRequired(authentication);
        TenantStorageConfig config = fileStorageService.getConfig(tenant);
        return StorageConfigResponse.from(config, storageProperties.getDefaultQuotaMb());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @RequiresPrivilege("platform.storage.manage")
    @Operation(summary = "Update tenant storage backend, credentials, or quota")
    StorageConfigResponse updateConfig(
            Authentication authentication,
            @Valid @RequestBody StorageConfigRequest request) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        TenantStorageConfig updated = fileStorageService.updateConfig(
                tenant,
                request.getBackend(),
                request.getCredentials(),
                request.getQuotaMb());
        return StorageConfigResponse.from(updated, storageProperties.getDefaultQuotaMb());
    }
}
