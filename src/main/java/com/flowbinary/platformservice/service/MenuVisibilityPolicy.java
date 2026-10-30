package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuAccessEffect;
import com.flowbinary.platformservice.model.MenuAudienceMode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides whether a single menu item may appear for a caller. Pure and
 * stateless: everything it needs arrives in {@link Subject}, so it is tested
 * without a database and can later back an "explain why hidden" preview.
 *
 * <p>Navigation follows privileges (design §19.1). Steps run in a fixed order and
 * the first failure wins:
 * <ol>
 *   <li>navigation flag — {@code is_visible}; for a temporary menu, its status and
 *       time window against {@code now} (checked here, not trusted to the scheduler);</li>
 *   <li><b>mandatory gates</b>, never overridable: tenant type, plan, feature, and the
 *       required privilege among the user's effective privileges. An item with no
 *       required privilege is visible to every active member;</li>
 *   <li>a temporary menu restricted to named users needs a user-level GRANT;</li>
 *   <li>a user-level DENY hides the item;</li>
 *   <li>a tenant hide, unless the user has a user-level GRANT.</li>
 * </ol>
 * Liveness (active, not soft-deleted) is applied by the catalogue query.
 */
public final class MenuVisibilityPolicy {

    private MenuVisibilityPolicy() {
    }

    public enum Verdict {
        VISIBLE, NOT_IN_NAVIGATION, NOT_LIVE, TENANT_TYPE, PLAN, FEATURE, PRIVILEGE, NOT_AUDIENCE, USER_DENIED, TENANT_HIDDEN
    }

    /** Everything about the caller that the policy consults. */
    public record Subject(Set<String> privileges,
                          boolean allPrivileges,
                          /** {@link com.flowbinary.platformservice.model.TenantType} name; null = unknown, passes only ungated items. */
                          String tenantType,
                          String planCode,
                          Set<String> enabledFeatures,
                          Map<UUID, MenuAccessEffect> userOverrides,
                          Map<UUID, CatalogOverride> tenantOverrides,
                          Instant now) {
    }

    public static Verdict evaluate(CatalogItem item, Subject s) {
        Verdict gate = gates(item, s);
        if (gate != null) return gate;

        MenuAccessEffect effect = s.userOverrides().get(item.id());
        if (item.temporary() && item.audience() == MenuAudienceMode.RESTRICTED && effect != MenuAccessEffect.GRANT) {
            return Verdict.NOT_AUDIENCE;
        }
        if (effect == MenuAccessEffect.DENY) return Verdict.USER_DENIED;

        CatalogOverride override = s.tenantOverrides().get(item.id());
        if (override != null && override.hidden() && effect != MenuAccessEffect.GRANT) return Verdict.TENANT_HIDDEN;

        return Verdict.VISIBLE;
    }

    /** The mandatory gates; null when the item passes all of them. */
    private static Verdict gates(CatalogItem item, Subject s) {
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
        if (required != null && !s.allPrivileges() && !s.privileges().contains(required)) return Verdict.PRIVILEGE;
        return null;
    }
}
