package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.model.DeltaEffect;

import java.util.*;

/**
 * The one place that knows how roles become privileges. Pure: no database, no
 * clock, so the rule is tested exhaustively on its own.
 *
 * <pre>
 * granted   = ⋃ (template privileges ∪ GRANT deltas)   over the held roles
 * denied    = ⋃ DENY deltas                            over the held roles
 * effective = (granted − denied) ∩ privileges usable in the scope ∩ privileges whose feature is enabled
 * </pre>
 *
 * A DENY from any held role wins over every GRANT, including the primary role's.
 */
public final class PermissionCalculator {

    private PermissionCalculator() {
    }

    /** One held role, reduced to what it contributes. */
    public record RoleContribution(Set<String> templatePrivileges, Map<String, DeltaEffect> deltas) {
        public RoleContribution {
            templatePrivileges = templatePrivileges == null ? Set.of() : templatePrivileges;
            deltas = deltas == null ? Map.of() : deltas;
        }
    }

    /**
     * Where the privileges will be used: a tenant of a given type, or the platform
     * ({@code tenantType} null). Tenant scope keeps only privileges a tenant role may
     * hold in that type; platform scope keeps only platform-only privileges.
     */
    public record Scope(String tenantType, Set<String> enabledFeatures) {
        public static Scope platform() {
            return new Scope(null, Set.of());
        }

        public Scope {
            enabledFeatures = enabledFeatures == null ? Set.of() : enabledFeatures;
        }

        /** Deprecated privileges still count: they stay enforced until removed (design §10). */
        boolean admits(AuthzPrivilege p) {
            if (tenantType == null) return !p.isAssignableByTenant();
            if (!p.usableIn(tenantType)) return false;
            return p.getFeatureCode() == null || enabledFeatures.contains(p.getFeatureCode());
        }
    }

    public static Set<String> effective(Collection<RoleContribution> roles,
                                        Map<String, AuthzPrivilege> catalogue,
                                        Scope scope) {
        Set<String> granted = new HashSet<>();
        Set<String> denied = new HashSet<>();
        for (RoleContribution role : roles) {
            granted.addAll(role.templatePrivileges());
            role.deltas().forEach((code, effect) -> {
                if (effect == DeltaEffect.DENY) denied.add(code);
                else granted.add(code);
            });
        }
        granted.removeAll(denied);

        Set<String> effective = new TreeSet<>();
        for (String code : granted) {
            AuthzPrivilege p = catalogue.get(code);
            if (p != null && scope.admits(p)) effective.add(code);
        }
        return Collections.unmodifiableSet(effective);
    }
}
