package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuAccessEffect;
import com.flowbinary.platformservice.model.MenuItem;
import com.flowbinary.platformservice.model.MenuTenantOverride;
import com.flowbinary.platformservice.model.Privilege;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a single menu item may appear for a caller. Pure and
 * stateless: everything it needs arrives in {@link Subject}, so it is tested
 * without a database and can later back an "explain why hidden" preview.
 *
 * <p>Steps run in a fixed order and the first failure wins:
 * <ol>
 *   <li>navigation flag — {@code is_visible};</li>
 *   <li><b>mandatory gates</b>, never overridable by an assignment: plan, feature, privilege;</li>
 *   <li>explicit user DENY;</li>
 *   <li>grant — a role the user holds, or a user-level GRANT;</li>
 *   <li>tenant hide.</li>
 * </ol>
 * Liveness (active, not soft-deleted) is applied by the catalogue query.
 */
public final class MenuVisibilityPolicy {

    private MenuVisibilityPolicy() {
    }

    public enum Verdict {
        VISIBLE, NOT_IN_NAVIGATION, PLAN, FEATURE, PRIVILEGE, USER_DENIED, NOT_GRANTED, TENANT_HIDDEN
    }

    /** Everything about the caller that the policy consults. */
    public record Subject(Set<UUID> roleIds,
                          Set<String> privileges,
                          boolean allPrivileges,
                          String planCode,
                          Set<String> enabledFeatures,
                          Map<UUID, MenuAccessEffect> userOverrides,
                          Map<UUID, MenuTenantOverride> tenantOverrides) {
    }

    public static Verdict evaluate(MenuItem item, Subject s) {
        if (!Boolean.TRUE.equals(item.getIsVisible())) return Verdict.NOT_IN_NAVIGATION;

        boolean planAllowed = item.getPlanAccess().stream()
                .anyMatch(pa -> pa.getId().getPlan().name().equalsIgnoreCase(s.planCode()));
        if (!planAllowed) return Verdict.PLAN;

        String feature = item.getRequiredFeature();
        if (feature != null && !s.enabledFeatures().contains(feature)) return Verdict.FEATURE;

        Privilege required = item.getRequiredPrivilege();
        if (required != null && !s.allPrivileges() && !s.privileges().contains(required.getCode())) {
            return Verdict.PRIVILEGE;
        }

        MenuAccessEffect effect = s.userOverrides().get(item.getId());
        if (effect == MenuAccessEffect.DENY) return Verdict.USER_DENIED;

        // Compare on the FK in the composite id — no need to load each Role.
        boolean granted = s.allPrivileges()
                || effect == MenuAccessEffect.GRANT
                || item.getRoleAccess().stream().anyMatch(ra -> s.roleIds().contains(ra.getId().getRoleId()));
        if (!granted) return Verdict.NOT_GRANTED;

        MenuTenantOverride override = s.tenantOverrides().get(item.getId());
        if (override != null && Boolean.TRUE.equals(override.getIsHidden())) return Verdict.TENANT_HIDDEN;

        return Verdict.VISIBLE;
    }
}
