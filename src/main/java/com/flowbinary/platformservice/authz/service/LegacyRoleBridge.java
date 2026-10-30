package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.Membership;
import com.flowbinary.platformservice.authz.model.MembershipStatus;
import com.flowbinary.platformservice.authz.repository.RoleTemplateRepository;
import com.flowbinary.platformservice.model.PlatformRole;
import com.flowbinary.platformservice.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Keeps the authorization model in step with flows that still speak the old
 * single-role language (registration, the user API), until phase 3 moves user
 * administration here. The mapping is the same one the V31 backfill uses.
 */
@Service
@RequiredArgsConstructor
public class LegacyRoleBridge {

    /** Old realm-role name → template code, per tenant type. Admin roles are handled separately. */
    private static final Map<String, Map<PlatformRole, String>> MAPPING = Map.of(
            "CLINIC", Map.of(
                    PlatformRole.CLINIC_ADMIN, "clinic_admin",
                    PlatformRole.DOCTOR, "doctor",
                    PlatformRole.NURSE, "nurse",
                    PlatformRole.RECEPTIONIST, "receptionist",
                    PlatformRole.BILLING_STAFF, "billing_staff",
                    PlatformRole.MEDICAL_CODER, "medical_coder",
                    PlatformRole.COMPLIANCE_OFFICER, "compliance_officer"),
            "LAB", Map.of(
                    PlatformRole.LAB_ADMIN, "lab_admin",
                    PlatformRole.LAB_TECHNICIAN, "lab_technician",
                    PlatformRole.PHLEBOTOMIST, "phlebotomist",
                    PlatformRole.RECEPTIONIST, "front_desk"),
            "STORE", Map.of(
                    PlatformRole.PHARMACIST, "pharmacist",
                    PlatformRole.RECEPTIONIST, "counter_staff"));

    private final RoleTemplateRepository templates;
    private final TenantRoleProvisioner provisioner;
    private final MembershipService memberships;

    /** A new tenant: its template roles, and its creator as the type's admin. */
    @Transactional
    public void onTenantCreated(Tenant tenant, UUID adminSub) {
        provisioner.provision(tenant);
        assignPrimary(tenant, adminSub, PlatformRole.TENANTADMIN);
    }

    /** A user added through the user API. Patients are not members. */
    @Transactional
    public void onUserCreated(Tenant tenant, UUID userSub, PlatformRole role) {
        if (role == PlatformRole.PATIENT) return;
        assignPrimary(tenant, userSub, role);
    }

    /** The user API changed someone's single role: it becomes their primary role. */
    @Transactional
    public void onUserRoleChanged(Tenant tenant, UUID userSub, PlatformRole role) {
        if (role == PlatformRole.PATIENT) return;
        assignPrimary(tenant, userSub, role);
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

    /** The template a legacy role maps to in a tenant type. */
    public String templateCode(String tenantType, PlatformRole role) {
        if (role == PlatformRole.TENANTADMIN || role == PlatformRole.SUPERADMIN) {
            return templates.findByTenantTypeAndAdminTrue(tenantType).orElseThrow().getCode();
        }
        String mapped = MAPPING.getOrDefault(tenantType, Map.of()).get(role);
        return mapped != null ? mapped : templates.findByTenantTypeAndFallbackTrue(tenantType).orElseThrow().getCode();
    }

    private void assignPrimary(Tenant tenant, UUID userSub, PlatformRole role) {
        String code = templateCode(EffectivePermissionService.tenantType(tenant), role);
        AuthzRole target = provisioner.templateRole(tenant, code);
        Membership membership = memberships.ensure(tenant.getId(), userSub, null);
        memberships.setPrimary(membership, target, null);
    }
}
