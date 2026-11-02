package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.repository.AuthzPrivilegeRepository;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Database-backed {@link MenuReferences}. */
@Component
@RequiredArgsConstructor
public class MenuReferenceLookup implements MenuReferences {

    private final AuthzPrivilegeRepository privilegeRepository;
    private final FeatureRepository    featureRepository;
    private final TenantUserRepository tenantUserRepository;
    private final MenuItemRepository   menuItemRepository;
    private final MenuModuleRepository menuModuleRepository;

    @Override
    public Optional<AuthzPrivilege> privilege(String code) {
        return privilegeRepository.findById(code).filter(AuthzPrivilege::isActive);
    }

    @Override
    public boolean featureExists(String code) {
        return featureRepository.existsByCode(code);
    }

    @Override
    public Optional<TenantUser> tenantUser(UUID tenantId, UUID userId) {
        return tenantUserRepository.findById(userId)
                .filter(u -> u.getTenant().getId().equals(tenantId));
    }

    @Override
    public boolean codeUsedByAnyTenant(UUID applicationId, String code) {
        return menuItemRepository.existsTenantOwnedCode(applicationId, code);
    }

    @Override
    public Optional<String> moduleNamespaceOf(String code) {
        int dot = code.indexOf('.');
        if (dot <= 0) return Optional.empty();
        String head = code.substring(0, dot);
        return menuModuleRepository.existsByCode(head) ? Optional.of(head) : Optional.empty();
    }

    @Override
    public boolean hasTenantOwnedChildren(Collection<UUID> parentIds) {
        return !parentIds.isEmpty() && menuItemRepository.existsTenantOwnedChild(parentIds);
    }
}
