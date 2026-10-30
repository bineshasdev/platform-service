package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.RoleKind;
import com.flowbinary.platformservice.authz.model.RoleTemplate;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gives a tenant one TEMPLATE role per template of its type. Idempotent: a
 * template the tenant already has is left alone, so re-running after a new
 * template ships adds just that one.
 */
@Service
@RequiredArgsConstructor
public class TenantRoleProvisioner {

    private final RoleTemplateRepository templates;
    private final AuthzRoleRepository roles;
    private final AuthzEvents events;

    /** @return the tenant's template roles keyed by template code */
    @Transactional
    public Map<String, AuthzRole> provision(Tenant tenant) {
        String type = EffectivePermissionService.tenantType(tenant);
        Map<String, AuthzRole> out = new LinkedHashMap<>();
        boolean created = false;
        for (RoleTemplate t : templates.findByTenantTypeOrderBySortOrderAsc(type)) {
            AuthzRole role = roles.findByTenantIdAndTemplateIdAndKind(tenant.getId(), t.getId(), RoleKind.TEMPLATE)
                    .orElse(null);
            if (role == null) {
                role = new AuthzRole();
                role.setTenantId(tenant.getId());
                role.setTemplateId(t.getId());
                role.setKind(RoleKind.TEMPLATE);
                role.setCode(t.getCode());
                role.setName(t.getName());
                role.setDescription(t.getDescription());
                role = roles.save(role);
                created = true;
            }
            out.put(t.getCode(), role);
        }
        if (created) events.tenantChanged(tenant.getId(), "TENANT_PROVISIONED");
        return out;
    }

    /** The tenant's TEMPLATE role for {@code templateCode}, provisioning the tenant first if needed. */
    @Transactional
    public AuthzRole templateRole(Tenant tenant, String templateCode) {
        AuthzRole role = provision(tenant).get(templateCode);
        if (role == null) {
            throw new IllegalStateException("No template '" + templateCode + "' for tenant type "
                    + EffectivePermissionService.tenantType(tenant));
        }
        return role;
    }
}
