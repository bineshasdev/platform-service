package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.Membership;
import com.flowbinary.platformservice.authz.model.MembershipStatus;
import com.flowbinary.platformservice.authz.model.RoleKind;
import com.flowbinary.platformservice.authz.repository.MembershipRepository;
import com.flowbinary.platformservice.exception.AuthzRuleException;
import com.flowbinary.platformservice.exception.AuthzRuleException.Rule;
import com.flowbinary.platformservice.model.Tenant;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The checks every role or membership change goes through (design §18.9).
 * Each throws {@link AuthzRuleException} naming the rule it enforces.
 */
@Component
@RequiredArgsConstructor
public class AuthzGuardrails {

    /** Someone in every tenant must keep both of these. */
    public static final Set<String> ADMIN_PRIVILEGES = Set.of("platform.member.manage", "platform.role.manage");

    private final MembershipRepository memberships;
    private final EffectivePermissionService permissions;
    private final TenantFeatures features;
    private final EntityManager em;

    /** Every code exists and may sit in a role of this tenant. */
    public void privilegesUsableIn(Tenant tenant, Collection<String> codes, Map<String, AuthzPrivilege> catalogue) {
        String type = EffectivePermissionService.tenantType(tenant);
        for (String code : codes) {
            AuthzPrivilege p = catalogue.get(code);
            if (p == null) throw new AuthzRuleException(Rule.UNKNOWN_PRIVILEGE, "Unknown privilege: " + code);
            if (!p.isAssignableByTenant()) {
                throw new AuthzRuleException(Rule.PLATFORM_ONLY, code + " is a platform privilege and cannot be in a tenant role");
            }
            if (!p.usableIn(type)) {
                throw new AuthzRuleException(Rule.TENANT_TYPE, code + " does not exist for " + type + " tenants");
            }
        }
    }

    /**
     * No self-escalation: a change to the editor's <em>own</em> access may only
     * involve privileges they already hold. Changes to other members are not
     * limited this way: an admin must be able to make someone a doctor without
     * being one (templates are platform-vetted, and every change is audited).
     */
    public void noSelfEscalation(PermissionSet editor, Collection<String> codes) {
        Set<String> missing = new TreeSet<>(codes);
        missing.removeAll(editor.privileges());
        if (!missing.isEmpty()) {
            throw new AuthzRuleException(Rule.ESCALATION, "You cannot give yourself privileges you do not hold: " + missing);
        }
    }

    /** Editing derived or custom roles is a plan feature; on a downgrade existing roles stay but become read-only. */
    public void entitled(Tenant tenant, RoleKind kind) {
        String feature = switch (kind) {
            case DERIVED -> TenantFeatures.DERIVED_ROLES;
            case CUSTOM -> TenantFeatures.CUSTOM_ROLES;
            default -> null;
        };
        if (feature != null && !features.enabled(tenant).contains(feature)) {
            throw new AuthzRuleException(Rule.ENTITLEMENT,
                    "Your plan does not include editing " + kind.name().toLowerCase() + " roles");
        }
    }

    public void primaryEligible(AuthzRole role) {
        if (!role.isPrimaryEligible()) {
            throw new AuthzRuleException(Rule.PRIMARY_KIND, "A " + role.getKind() + " role cannot be a primary role");
        }
    }

    /**
     * Call after applying a change, inside its transaction: if no active member
     * still holds every admin privilege, the change is refused and rolls back.
     */
    public void someoneStillAdministers(Tenant tenant) {
        em.flush();
        for (Membership m : memberships.findByTenantIdAndStatus(tenant.getId(), MembershipStatus.ACTIVE)) {
            PermissionSet set = permissions.forTenant(tenant.getId(), m.getUserSub());
            if (set.privileges().containsAll(ADMIN_PRIVILEGES)) return;
        }
        throw new AuthzRuleException(Rule.LAST_ADMIN,
                "This change would leave nobody able to manage members and roles");
    }
}
