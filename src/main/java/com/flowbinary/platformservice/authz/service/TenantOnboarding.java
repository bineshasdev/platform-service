package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.Membership;
import com.flowbinary.platformservice.authz.model.MembershipStatus;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps memberships in step with the flows that create and remove tenants and users: registration
 * gives a new tenant its template roles and its creator the admin role; the user API gives each new
 * user a membership and a primary role.
 */
@Service
@RequiredArgsConstructor
public class TenantOnboarding {

    private final RoleTemplateRepository templates;
    private final AuthzRoleRepository roles;
    private final TenantRoleProvisioner provisioner;
    private final MembershipService memberships;

    /** A new tenant: its template roles, and its creator as the type's admin. */
    @Transactional
    public void onTenantCreated(Tenant tenant, UUID adminSub) {
        provisioner.provision(tenant);
        String type = EffectivePermissionService.tenantType(tenant);
        String adminTemplate = templates.findByTenantTypeAndAdminTrue(type).orElseThrow().getCode();
        memberships.setPrimary(memberships.ensure(tenant.getId(), adminSub, null),
                provisioner.templateRole(tenant, adminTemplate), null);
    }

    /**
     * A new member with {@code primaryRoleId} as primary role, or the type's least-privileged template
     * when none is given.
     *
     * @return the code of the primary role they now hold
     */
    @Transactional
    public String onMemberCreated(Tenant tenant, UUID userSub, UUID primaryRoleId, UUID actor) {
        AuthzRole role = primaryRoleId == null ? fallbackRole(tenant) : roles.findById(primaryRoleId)
                .filter(r -> tenant.getId().equals(r.getTenantId()) && r.isActive())
                .orElseThrow(() -> new IllegalArgumentException("Role not found in this tenant: " + primaryRoleId));
        memberships.setPrimary(memberships.ensure(tenant.getId(), userSub, actor), role, actor);
        return role.getCode();
    }

    /** The user API changed someone's status: only ACTIVE users keep their privileges. */
    @Transactional
    public void onUserStatusChanged(Tenant tenant, UUID userSub, String status) {
        memberships.find(tenant.getId(), userSub).ifPresent(m -> memberships.setStatus(m,
                "ACTIVE".equalsIgnoreCase(status) ? MembershipStatus.ACTIVE : MembershipStatus.SUSPENDED));
    }

    /** The user API removed someone from the tenant. */
    @Transactional
    public void onUserDeleted(Tenant tenant, UUID userSub) {
        memberships.find(tenant.getId(), userSub).ifPresent(memberships::remove);
    }

    private AuthzRole fallbackRole(Tenant tenant) {
        String type = EffectivePermissionService.tenantType(tenant);
        return provisioner.templateRole(tenant, templates.findByTenantTypeAndFallbackTrue(type).orElseThrow().getCode());
    }
}
