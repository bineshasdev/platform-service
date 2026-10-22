package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.repository.AppSettingRepository;
import com.flowbinary.platformservice.repository.TenantSettingRepository;
import com.flowbinary.platformservice.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Merges settings in precedence order: application → tenant → user. */
@Component
@RequiredArgsConstructor
public class SettingsResolver {

    private final AppSettingRepository    appSettingRepository;
    private final TenantSettingRepository tenantSettingRepository;
    private final UserSettingRepository   userSettingRepository;

    @Transactional(readOnly = true)
    public Map<String, String> merged(UUID tenantId, UUID authUserId) {
        Map<String, String> merged = new LinkedHashMap<>();
        appSettingRepository.findAllWithDefinition()
                .forEach(s -> merged.put(s.getDefinition().getKey(), s.getValue()));
        tenantSettingRepository.findAllByTenantId(tenantId)
                .forEach(s -> merged.put(s.getDefinition().getKey(), s.getValue()));
        userSettingRepository.findByUserId(authUserId)
                .forEach(s -> merged.put(s.getDefinition().getKey(), s.getValue()));
        return merged;
    }
}
