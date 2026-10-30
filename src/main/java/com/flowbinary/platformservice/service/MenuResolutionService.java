package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.PermissionCalculator;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.RoleContribution;
import com.flowbinary.platformservice.authz.service.PermissionCalculator.Scope;
import com.flowbinary.platformservice.authz.service.PermissionSet;
import com.flowbinary.platformservice.authz.service.TenantFeatures;
import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves the effective menu for a (user, application, tenant) triple.
 *
 * <p>Navigation follows privileges (design §9, §19). An item is included when
 * {@link MenuVisibilityPolicy} lets it through: it is live, matches the tenant's
 * type and plan, its feature is enabled, and the user holds the privilege it
 * requires in the authorization model, with user-level and tenant overrides
 * applied last. There is no role-to-menu mapping.
 * {@link MenuTreeBuilder} then assembles the tree, prunes parents left without
 * children, applies tenant relabel/reorder and localises labels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuResolutionService {

    /** Application a caller resolves against when it does not name one. */
    public static final String DEFAULT_APPLICATION = "PROVIDER_CONSOLE";

    private final MenuUserAccessRepository      menuUserAccessRepository;
    private final EffectivePermissionService    permissions;
    private final TenantFeatures                tenantFeatures;
    private final MenuCatalogueCache            catalogueCache;
    private final MenuMetrics                   metrics;

    /** Everything the resolver needs that it cannot look up itself. */
    public record MenuContext(Tenant tenant,
                              TenantUser user,
                              String applicationCode,
                              String planCode,
                              Set<String> enabledFeatures,
                              String locale) {
    }

    // ── Privileges ─────────────────────────────────────────────────────────

    /**
     * Privilege codes the user holds in their tenant, in the authorization model's
     * codes. A platform superadmin holds every privilege ({@code "*"}); a user whose
     * membership is missing or not active holds none.
     */
    @Transactional(readOnly = true)
    public Set<String> effectivePrivileges(TenantUser user) {
        if (user.getRole() == PlatformRole.SUPERADMIN) return Set.of("*");
        return memberPrivileges(user.getTenant().getId(), user.getAuthUserId()).privileges();
    }

    private PermissionSet memberPrivileges(UUID tenantId, UUID userSub) {
        return permissions.forTenant(tenantId, userSub);
    }

    // ── Menu resolution ────────────────────────────────────────────────────

    /**
     * The caller's menu. The shared catalogue comes from the version-keyed cache;
     * everything that depends on <em>who</em> is asking (privileges, user overrides)
     * is read per request. A user without an active membership gets an empty menu.
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> resolve(MenuContext ctx) {
        return metrics.observe("menu.resolve", () -> {
            long start = System.nanoTime();
            log.debug("menu.resolve start app={} tenant={} user={}",
                    ctx.applicationCode(), ctx.tenant().getId(), ctx.user().getId());
            MenuCatalogue catalogue = catalogueCache.get(ctx.applicationCode(), ctx.tenant().getId());
            log.debug("menu.resolve catalogue ready items={} in {} ms", catalogue.items().size(), elapsedMs(start));
            List<MenuEntry> tree = resolve(ctx, catalogue);
            log.debug("menu.resolve done entries={} total {} ms", tree.size(), elapsedMs(start));
            return tree;
        }, "application", ctx.applicationCode());
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Like {@link #resolve} but reads the catalogue straight from the database,
     * so it sees changes made earlier in the current transaction. Admin preview only.
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> resolveUncached(MenuContext ctx) {
        return resolve(ctx, catalogueCache.compileFresh(ctx.applicationCode(), ctx.tenant().getId()));
    }

    private List<MenuEntry> resolve(MenuContext ctx, MenuCatalogue catalogue) {
        boolean allPrivileges = ctx.user().getRole() == PlatformRole.SUPERADMIN;
        PermissionSet set = allPrivileges ? null : memberPrivileges(ctx.tenant().getId(), ctx.user().getAuthUserId());
        if (set != null && set.status() != PermissionSet.Status.ACTIVE) {
            log.debug("menu.resolve no active membership ({}): empty menu", set.status());
            return List.of();
        }
        Set<String> privileges = allPrivileges ? Set.of() : set.privileges();
        log.debug("menu.resolve privileges={}", privileges.size());

        Instant now = Instant.now();
        Map<UUID, MenuAccessEffect> userOverrides = menuUserAccessRepository
                .findByUserId(ctx.user().getId()).stream()
                .filter(a -> a.isActiveAt(now))
                .collect(Collectors.toMap(a -> a.getMenuItem().getId(),
                                          MenuUserAccess::getEffect,
                                          (a, b) -> b));
        log.debug("menu.resolve userOverrides={}", userOverrides.size());

        return resolveFor(catalogue, privileges, allPrivileges, userOverrides, ctx);
    }

    /**
     * The menu a holder of one role would see, with no user-level overrides. Used by
     * the admin preview (always uncached). The role's privileges are its template plus
     * deltas, limited to what the tenant's type and plan allow.
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> resolveForRole(AuthzRole role, MenuContext ctx) {
        Map<AuthzRole, RoleContribution> one = Map.of(role, permissions.contributionOf(role));
        Set<String> privileges = PermissionCalculator.effective(one.values(), permissions.catalogue(),
                new Scope(EffectivePermissionService.tenantType(ctx.tenant()), tenantFeatures.enabled(ctx.tenant())));
        return resolveFor(catalogueCache.compileFresh(ctx.applicationCode(), ctx.tenant().getId()),
                privileges, false, Map.of(), ctx);
    }

    private List<MenuEntry> resolveFor(MenuCatalogue catalogue, Set<String> privileges, boolean allPrivileges,
                                       Map<UUID, MenuAccessEffect> userOverrides, MenuContext ctx) {
        MenuVisibilityPolicy.Subject subject = new MenuVisibilityPolicy.Subject(
                privileges,
                allPrivileges,
                ctx.tenant().getType() == null ? null : ctx.tenant().getType().name(),
                ctx.planCode(),
                ctx.enabledFeatures(),
                userOverrides,
                catalogue.overrides(),
                Instant.now());

        Set<UUID> visible = catalogue.items().stream()
                .filter(m -> MenuVisibilityPolicy.evaluate(m, subject) == MenuVisibilityPolicy.Verdict.VISIBLE)
                .map(CatalogItem::id)
                .collect(Collectors.toSet());
        log.debug("menu.resolve visible={} of {} catalogue items, building tree",
                visible.size(), catalogue.items().size());

        return MenuTreeBuilder.build(catalogue.items(), visible, catalogue.overrides(), ctx.locale());
    }
}
