package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.*;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.repository.MembershipRepository;
import com.flowbinary.platformservice.authz.repository.RoleAssignmentRepository;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.RoleContribution;
import com.flowbinary.platformservice.exception.AuthzRuleException;
import com.flowbinary.platformservice.exception.AuthzRuleException.Rule;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.service.AuditService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Memberships and the roles they hold. System flows (registration, the legacy
 * user API) use {@link #ensure} and {@link #setPrimary}; tenant admins use
 * {@link #replaceAssignments}, which runs every guardrail.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    /** One extra role to hold, optionally until {@code validTo} and only at {@code locationIds}. */
    public record Extra(UUID roleId, Instant validTo, List<UUID> locationIds) {
    }

    /** The full set of roles a membership should hold after the change. */
    public record AssignmentChange(UUID primaryRoleId, List<Extra> extras) {
    }

    /** Why a member has, or lacks, one privilege. */
    public record Explanation(String privilege, boolean allowed, List<String> reasons) {
    }

    private final MembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final AuthzRoleRepository roles;
    private final EffectivePermissionService permissions;
    private final AuthzGuardrails guardrails;
    private final TenantFeatures features;
    private final AuthzEvents events;
    private final AuditService audit;
    private final EntityManager em;

    // ── System flows ─────────────────────────────────────────────────────

    /** The membership of {@code userSub} in the tenant (null = platform), created ACTIVE if missing. */
    @Transactional
    public Membership ensure(UUID tenantId, UUID userSub, UUID actor) {
        Optional<Membership> found = tenantId == null
                ? memberships.findPlatformMembership(userSub)
                : memberships.findByTenantIdAndUserSub(tenantId, userSub);
        return found.orElseGet(() -> {
            Membership m = new Membership();
            m.setTenantId(tenantId);
            m.setUserSub(userSub);
            m.setStatus(MembershipStatus.ACTIVE);
            m.setCreatedBy(actor);
            Membership saved = memberships.save(m);
            events.memberChanged(tenantId, userSub, "MEMBERSHIP_CREATED");
            return saved;
        });
    }

    /** Makes {@code role} the primary role, keeping any extras. No guardrails: for system flows. */
    @Transactional
    public void setPrimary(Membership membership, AuthzRole role, UUID actor) {
        guardrails.primaryEligible(role);
        List<RoleAssignment> held = assignments.findByMembershipId(membership.getId());
        RoleAssignment current = held.stream().filter(RoleAssignment::isPrimary).findFirst().orElse(null);
        if (current != null && current.getRoleId().equals(role.getId())) return;

        if (current != null) {
            assignments.delete(current);
            em.flush();   // the one-primary index must not see two at once
        }
        // The role may already be held as an extra: promote it.
        RoleAssignment promoted = held.stream().filter(a -> a.getRoleId().equals(role.getId())).findFirst().orElse(null);
        if (promoted != null) {
            promoted.setPrimary(true);
            promoted.setValidTo(null);
            assignments.save(promoted);
        } else {
            assignments.save(assignment(membership.getId(), role.getId(), true, null, List.of(), actor));
        }
        events.memberChanged(membership.getTenantId(), membership.getUserSub(), "PRIMARY_ROLE_CHANGED");
    }

    @Transactional(readOnly = true)
    public Optional<Membership> find(UUID tenantId, UUID userSub) {
        return memberships.findByTenantIdAndUserSub(tenantId, userSub);
    }

    /** Ends a membership and everything it held. */
    @Transactional
    public void remove(Membership membership) {
        memberships.delete(membership);
        events.memberChanged(membership.getTenantId(), membership.getUserSub(), "MEMBERSHIP_REMOVED");
    }

    /** Sets status; a suspended member keeps their roles but holds no privileges. */
    @Transactional
    public void setStatus(Membership membership, MembershipStatus status) {
        membership.setStatus(status);
        memberships.save(membership);
        events.memberChanged(membership.getTenantId(), membership.getUserSub(), "MEMBERSHIP_STATUS_CHANGED");
    }

    // ── Tenant administration ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Membership> list(Tenant tenant) {
        return memberships.findByTenantIdOrderByCreatedAtAsc(tenant.getId());
    }

    @Transactional(readOnly = true)
    public List<RoleAssignment> assignmentsOf(UUID membershipId) {
        return assignments.findByMembershipId(membershipId);
    }

    @Transactional(readOnly = true)
    public Membership get(Tenant tenant, UUID membershipId) {
        return memberships.findById(membershipId)
                .filter(m -> tenant.getId().equals(m.getTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Membership not found: " + membershipId));
    }

    /**
     * Replaces everything a member holds. Every role must belong to the tenant;
     * the primary must be TEMPLATE or DERIVED; editors cannot widen their own
     * access; and someone must still administer the tenant.
     */
    @Transactional
    public List<RoleAssignment> replaceAssignments(Tenant tenant, UUID membershipId, AssignmentChange change,
                                                   PermissionSet editor, UUID actor) {
        Membership membership = get(tenant, membershipId);
        if (change.primaryRoleId() == null) {
            throw new AuthzRuleException(Rule.PRIMARY_REQUIRED, "A member must have a primary role");
        }
        List<Extra> extras = change.extras() == null ? List.of() : change.extras();

        Set<UUID> roleIds = new LinkedHashSet<>();
        roleIds.add(change.primaryRoleId());
        extras.forEach(e -> roleIds.add(e.roleId()));
        if (roleIds.size() != extras.size() + 1) {
            throw new IllegalArgumentException("Each role can be held only once");
        }
        Map<UUID, AuthzRole> byId = new HashMap<>();
        for (AuthzRole r : roles.findByIdIn(roleIds)) byId.put(r.getId(), r);
        for (UUID id : roleIds) {
            AuthzRole r = byId.get(id);
            if (r == null || !tenant.getId().equals(r.getTenantId()) || !r.isActive()) {
                throw new IllegalArgumentException("Role not found in this tenant: " + id);
            }
        }
        guardrails.primaryEligible(byId.get(change.primaryRoleId()));

        if (membership.getUserSub().equals(actor)) checkSelfEscalation(membershipId, roleIds, byId, editor);

        List<RoleAssignment> old = assignments.findByMembershipId(membershipId);
        assignments.deleteAll(old);
        em.flush();
        List<RoleAssignment> saved = new ArrayList<>();
        saved.add(assignments.save(assignment(membershipId, change.primaryRoleId(), true, null, List.of(), actor)));
        for (Extra e : extras) {
            saved.add(assignments.save(assignment(membershipId, e.roleId(), false, e.validTo(),
                    e.locationIds() == null ? List.of() : e.locationIds(), actor)));
        }

        guardrails.someoneStillAdministers(tenant);

        audit.record(new AuditService.Entry(tenant.getId(), actor, "AUTHZ_ASSIGNMENTS_REPLACED", "authz_membership",
                membershipId.toString(), describe(old), describe(saved), null, "API"));
        events.memberChanged(tenant.getId(), membership.getUserSub(), "ASSIGNMENTS_CHANGED");
        return saved;
    }

    /** Walks the rule for one privilege and says, in words, which role and which filter decided. */
    @Transactional(readOnly = true)
    public Explanation explain(Tenant tenant, UUID membershipId, String privilege) {
        Membership membership = get(tenant, membershipId);
        Map<String, AuthzPrivilege> catalogue = permissions.catalogue();
        AuthzPrivilege p = catalogue.get(privilege);
        List<String> reasons = new ArrayList<>();
        if (p == null) return new Explanation(privilege, false, List.of("No such privilege"));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            reasons.add("Membership is " + membership.getStatus());
        }

        boolean granted = false;
        boolean denied = false;
        for (Map.Entry<AuthzRole, RoleContribution> e : permissions.contributions(membershipId, Instant.now()).entrySet()) {
            AuthzRole role = e.getKey();
            RoleContribution c = e.getValue();
            DeltaEffect delta = c.deltas().get(privilege);
            if (delta == DeltaEffect.DENY) {
                denied = true;
                reasons.add("Denied by role '" + role.getCode() + "'");
            } else if (delta == DeltaEffect.GRANT) {
                granted = true;
                reasons.add("Granted by role '" + role.getCode() + "'");
            } else if (c.templatePrivileges().contains(privilege)) {
                granted = true;
                reasons.add("Granted by the template of role '" + role.getCode() + "'");
            }
        }
        if (!granted) reasons.add("No held role grants it");
        String type = EffectivePermissionService.tenantType(tenant);
        if (!p.usableIn(type)) reasons.add("Not available for " + type + " tenants");
        if (p.getFeatureCode() != null && !features.enabled(tenant).contains(p.getFeatureCode())) {
            reasons.add("Needs plan feature " + p.getFeatureCode());
        }
        boolean allowed = permissions.forTenant(tenant.getId(), membership.getUserSub()).has(privilege);
        if (allowed && !denied) reasons.add(0, "Allowed");
        return new Explanation(privilege, allowed, reasons);
    }

    /** Editing your own roles: newly held roles may only carry privileges you already hold. */
    private void checkSelfEscalation(UUID membershipId, Set<UUID> roleIds, Map<UUID, AuthzRole> byId, PermissionSet editor) {
        Set<UUID> before = new HashSet<>();
        assignments.findByMembershipId(membershipId).forEach(a -> before.add(a.getRoleId()));
        Set<String> gained = new HashSet<>();
        roleIds.stream()
                .filter(id -> !before.contains(id))   // keeping a role is not gaining one
                .forEach(id -> gained.addAll(grants(byId.get(id))));
        guardrails.noSelfEscalation(editor, gained);
    }

    /** Everything a role would let its holder do, before tenant-type and plan filters. */
    Set<String> grants(AuthzRole role) {
        Set<String> out = new HashSet<>();
        if (role.getTemplateId() != null) out.addAll(permissions.templatePrivilegesOf(role.getTemplateId()));
        role.getDeltas().forEach((code, effect) -> {
            if (effect == DeltaEffect.GRANT) out.add(code);
            else out.remove(code);
        });
        return out;
    }

    private static RoleAssignment assignment(UUID membershipId, UUID roleId, boolean primary, Instant validTo,
                                             List<UUID> locations, UUID actor) {
        RoleAssignment a = new RoleAssignment();
        a.setMembershipId(membershipId);
        a.setRoleId(roleId);
        a.setPrimary(primary);
        a.setValidTo(primary ? null : validTo);
        a.setLocationIds(new ArrayList<>(locations));
        a.setAssignedBy(actor);
        return a;
    }

    private static List<Map<String, Object>> describe(List<RoleAssignment> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RoleAssignment a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleId", a.getRoleId().toString());
            m.put("primary", a.isPrimary());
            if (a.getValidTo() != null) m.put("validTo", a.getValidTo().toString());
            if (!a.getLocationIds().isEmpty()) m.put("locations", a.getLocationIds().stream().map(UUID::toString).toList());
            out.add(m);
        }
        return out;
    }
}
