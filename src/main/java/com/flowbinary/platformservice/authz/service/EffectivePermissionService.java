package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.*;
import com.flowbinary.platformservice.authz.repository.*;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.RoleContribution;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.Scope;
import com.flowbinary.platformservice.authz.service.PermissionSet.Status;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantStatus;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes a {@link PermissionSet} from the database. Callers on the request
 * path go through the SDK's cache; this class always reads fresh.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final AuthzRoleRepository roles;
    private final RoleTemplateRepository templates;
    private final AuthzPrivilegeRepository privileges;
    private final TenantFeatures features;

    /** The tenant type used for templates and privileges; untyped legacy tenants count as clinics. */
    public static String tenantType(Tenant tenant) {
        return tenant.getType() == null ? "CLINIC" : tenant.getType().name();
    }

    @Transactional(readOnly = true)
    public PermissionSet forTenant(UUID tenantId, UUID userSub) {
        Instant now = Instant.now();
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.active) {
            return PermissionSet.denied(tenantId, userSub, null, Status.TENANT_INACTIVE, now);
        }
        Optional<Membership> membership = memberships.findByTenantIdAndUserSub(tenantId, userSub);
        return compute(tenantId, userSub, membership, new Scope(tenantType(tenant), features.enabled(tenant)), now);
    }

    @Transactional(readOnly = true)
    public PermissionSet forPlatform(UUID userSub) {
        Instant now = Instant.now();
        return compute(null, userSub, memberships.findPlatformMembership(userSub), Scope.platform(), now);
    }

    /** The privilege catalogue keyed by code. Small, read whole. */
    @Transactional(readOnly = true)
    public Map<String, AuthzPrivilege> catalogue() {
        return privileges.findAll().stream().collect(Collectors.toMap(AuthzPrivilege::getCode, Function.identity()));
    }

    /** What each held role contributes, for a membership; used by explain and the guardrails. */
    @Transactional(readOnly = true)
    public Map<AuthzRole, RoleContribution> contributions(UUID membershipId, Instant now) {
        List<RoleAssignment> held = assignments.findByMembershipId(membershipId).stream()
                .filter(a -> a.isActiveAt(now))
                .toList();
        Map<UUID, AuthzRole> byId = roles.findByIdIn(held.stream().map(RoleAssignment::getRoleId).toList()).stream()
                .filter(AuthzRole::isActive)
                .collect(Collectors.toMap(AuthzRole::getId, Function.identity()));
        Map<UUID, Set<String>> templatePrivileges = new HashMap<>();
        Map<AuthzRole, RoleContribution> out = new LinkedHashMap<>();
        held.stream()
                .sorted(Comparator.comparing(RoleAssignment::isPrimary).reversed())
                .map(a -> byId.get(a.getRoleId()))
                .filter(Objects::nonNull)
                .forEach(role -> out.put(role, new RoleContribution(
                        role.getTemplateId() == null ? Set.of()
                                : templatePrivileges.computeIfAbsent(role.getTemplateId(), this::templatePrivilegesOf),
                        role.getDeltas())));
        return out;
    }

    /** What one role contributes on its own: its template's privileges plus its deltas. */
    @Transactional(readOnly = true)
    public RoleContribution contributionOf(AuthzRole role) {
        return new RoleContribution(role.getTemplateId() == null ? Set.of() : templatePrivilegesOf(role.getTemplateId()),
                role.getDeltas());
    }

    @Transactional(readOnly = true)
    public Set<String> templatePrivilegesOf(UUID templateId) {
        return templates.findById(templateId).map(t -> Set.copyOf(t.getPrivileges())).orElse(Set.of());
    }

    private PermissionSet compute(UUID tenantId, UUID userSub, Optional<Membership> found, Scope scope, Instant now) {
        if (found.isEmpty()) return PermissionSet.denied(tenantId, userSub, null, Status.NO_MEMBERSHIP, now);
        Membership membership = found.get();
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return PermissionSet.denied(tenantId, userSub, membership.getId(), Status.MEMBERSHIP_INACTIVE, now);
        }

        Map<AuthzRole, RoleContribution> held = contributions(membership.getId(), now);
        Set<String> effective = PermissionCalculator.effective(held.values(), catalogue(), scope);

        UUID primaryRoleId = assignments.findByMembershipId(membership.getId()).stream()
                .filter(RoleAssignment::isPrimary).map(RoleAssignment::getRoleId).findFirst().orElse(null);
        String primary = held.keySet().stream()
                .filter(r -> r.getId().equals(primaryRoleId)).map(AuthzRole::getCode).findFirst().orElse(null);
        List<String> roleCodes = held.keySet().stream().map(AuthzRole::getCode).toList();

        return new PermissionSet(tenantId, userSub, membership.getId(), Status.ACTIVE, effective, primary, roleCodes, now);
    }
}
