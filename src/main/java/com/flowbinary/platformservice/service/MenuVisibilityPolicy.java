package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuAccessEffect;
import com.flowbinary.platformservice.model.MenuAudienceMode;
import com.flowbinary.platformservice.model.MenuSource;

import java.time.Instant;
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
 *   <li>navigation flag — {@code is_visible}; for a temporary menu, its status and
 *       time window against {@code now} (checked here, not trusted to the scheduler);</li>
 *   <li><b>mandatory gates</b>, never overridable by an assignment: tenant type, plan, feature, privilege;</li>
 *   <li>explicit DENY — user-level or via any group; a DENY beats every grant;</li>
 *   <li>grant — a role the user holds, a user-level GRANT, or a group GRANT
 *       (a temporary menu with audience {@code ALL_ELIGIBLE} needs none, and neither does a
 *       module item: the privilege it must declare is its gate);</li>
 *   <li>tenant hide.</li>
 * </ol>
 * Liveness (active, not soft-deleted) is applied by the catalogue query.
 */
public final class MenuVisibilityPolicy {

    private MenuVisibilityPolicy() {
    }

    public enum Verdict {
        VISIBLE, NOT_IN_NAVIGATION, NOT_LIVE, TENANT_TYPE, PLAN, FEATURE, PRIVILEGE, USER_DENIED, GROUP_DENIED, NOT_GRANTED, TENANT_HIDDEN
    }

    /** Everything about the caller that the policy consults. */
    public record Subject(Set<UUID> roleIds,
                          Set<String> privileges,
                          boolean allPrivileges,
                          /** {@link com.flowbinary.platformservice.model.TenantType} name; null = unknown, passes only ungated items. */
                          String tenantType,
                          String planCode,
                          Set<String> enabledFeatures,
                          Map<UUID, MenuAccessEffect> userOverrides,
                          Map<UUID, CatalogOverride> tenantOverrides,
                          /** Items denied / granted to any active group the user belongs to. */
                          Set<UUID> groupDenied,
                          Set<UUID> groupGranted,
                          Instant now) {
    }

    public static Verdict evaluate(CatalogItem item, Subject s) {
        if (!item.visible()) return Verdict.NOT_IN_NAVIGATION;
        if (!item.isLiveAt(s.now())) return Verdict.NOT_LIVE;

        if (!item.tenantTypes().isEmpty()
                && (s.tenantType() == null || !item.tenantTypes().contains(s.tenantType()))) {
            return Verdict.TENANT_TYPE;
        }

        if (!item.plans().contains(s.planCode() == null ? "" : s.planCode().toLowerCase())) return Verdict.PLAN;

        String feature = item.requiredFeature();
        if (feature != null && !s.enabledFeatures().contains(feature)) return Verdict.FEATURE;

        String required = item.requiredPrivilege();
        if (required != null && !s.allPrivileges() && !s.privileges().contains(required)) {
            return Verdict.PRIVILEGE;
        }

        MenuAccessEffect effect = s.userOverrides().get(item.id());
        if (effect == MenuAccessEffect.DENY) return Verdict.USER_DENIED;
        if (s.groupDenied().contains(item.id())) return Verdict.GROUP_DENIED;

        boolean granted = s.allPrivileges()
                || effect == MenuAccessEffect.GRANT
                || s.groupGranted().contains(item.id())
                || (item.temporary() && item.audience() == MenuAudienceMode.ALL_ELIGIBLE)
                || item.source() == MenuSource.MODULE      // a module item is gated by its declared privilege
                || item.roleIds().stream().anyMatch(s.roleIds()::contains);
        if (!granted) return Verdict.NOT_GRANTED;

        CatalogOverride override = s.tenantOverrides().get(item.id());
        if (override != null && override.hidden()) return Verdict.TENANT_HIDDEN;

        return Verdict.VISIBLE;
    }
}
