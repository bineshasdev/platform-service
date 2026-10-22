package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.Privilege;
import com.flowbinary.platformservice.model.Role;
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

    private final RoleRepository       roleRepository;
    private final PrivilegeRepository  privilegeRepository;
    private final FeatureRepository    featureRepository;
    private final TenantUserRepository tenantUserRepository;
    private final MenuItemRepository   menuItemRepository;

    @Override
    public Optional<Role> assignableRole(MenuScope scope, String code) {
        if (scope.isGlobal()) {
            return roleRepository.findByTenantIsNullAndCode(code)
                    .filter(r -> r.getIsActive() && r.getIsAssignable());
        }
        return roleRepository.findAssignableForTenant(scope.tenantId()).stream()
                .filter(r -> r.getCode().equals(code))
                // A tenant's own role wins over a system role of the same code.
                .min((a, b) -> Boolean.compare(a.getTenant() == null, b.getTenant() == null));
    }

    @Override
    public Optional<Privilege> privilege(String code) {
        return privilegeRepository.findByCode(code).filter(Privilege::getIsActive);
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
    public boolean hasTenantOwnedChildren(Collection<UUID> parentIds) {
        return !parentIds.isEmpty() && menuItemRepository.existsTenantOwnedChild(parentIds);
    }
}
