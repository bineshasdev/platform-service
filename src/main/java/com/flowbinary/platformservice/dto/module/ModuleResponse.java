package com.flowbinary.platformservice.dto.module;

import com.flowbinary.platformservice.model.MenuModule;

import java.time.Instant;
import java.util.List;

public record ModuleResponse(String code, String name, String description, String application, String ownerClientId,
                             String manifestUrl, List<String> allowedRoutePrefixes, String privilegeModuleCode,
                             String status, String health, String currentVersion, Instant lastSyncedAt,
                             Instant lastAttemptAt, Instant nextAttemptAt, String lastError, int consecutiveFailures) {

    public static ModuleResponse of(MenuModule m) {
        return new ModuleResponse(m.getCode(), m.getName(), m.getDescription(), m.getApplication().getCode(),
                m.getOwnerClientId(), m.getManifestUrl(), List.copyOf(m.getAllowedRoutePrefixes()),
                m.getPrivilegeModuleCode(), m.getStatus().name(), m.getHealth().name(), m.getCurrentVersion(),
                m.getLastSyncedAt(), m.getLastAttemptAt(), m.getNextAttemptAt(), m.getLastError(),
                m.getConsecutiveFailures());
    }
}
