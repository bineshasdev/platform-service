package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves the effective menu for a (user, application, tenant) triple.
 *
 * <p>An item is included when all of the following hold:
 * <ol>
 *   <li>it is live in the requested application — active, not soft-deleted;</li>
 *   <li>{@link MenuVisibilityPolicy} lets it through (plan, feature, privilege, deny, grant, tenant hide).</li>
 * </ol>
 * {@link MenuTreeBuilder} then assembles the tree, prunes parents left without
 * children, applies tenant relabel/reorder and localises labels.
 */
@Service
@RequiredArgsConstructor
public class MenuResolutionService {

    /** Application a caller resolves against when it does not name one. */
    public static final String DEFAULT_APPLICATION = "PROVIDER_CONSOLE";

    private final MenuUserAccessRepository      menuUserAccessRepository;
    private final UserRoleAssignmentRepository  userRoleAssignmentRepository;
    private final RolePrivilegeRepository       rolePrivilegeRepository;
    private final RoleRepository                roleRepository;
    private final UserGroupMemberRepository     userGroupMemberRepository;
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

    // ── Roles and privileges ───────────────────────────────────────────────

    /**
     * The roles in force for a user right now, with inherited parent roles
     * folded in. Falls back to the {@code users.role} cache for users that
     * predate {@code user_roles}.
     */
    @Transactional(readOnly = true)
    public List<Role> effectiveRoles(TenantUser user) {
        List<Role> assigned = userRoleAssignmentRepository
                .findActiveByUserId(user.getId(), Instant.now())
                .stream()
                .map(UserRoleAssignment::getRole)
                .toList();

        if (!assigned.isEmpty()) return expandInherited(assigned);

        if (user.getRole() == null) return List.of();
        return roleRepository
                .findFirstByTenantIsNullAndPlatformRoleOrderBySortOrderAsc(user.getRole().roleName())
                .map(r -> expandInherited(List.of(r)))
                .orElseGet(List::of);
    }

    /** Privilege codes the user effectively holds across all their roles. */
    @Transactional(readOnly = true)
    public Set<String> effectivePrivileges(TenantUser user) {
        // A platform superadmin is not modelled through the privilege catalogue.
        if (user.getRole() == PlatformRole.SUPERADMIN) return Set.of("*");

        List<UUID> roleIds = effectiveRoles(user).stream().map(Role::getId).toList();
        return roleIds.isEmpty() ? Set.of()
                : rolePrivilegeRepository.findPrivilegeCodesByRoleIds(roleIds);
    }

    /** Walks {@code parent_role_id} chains, tolerating an accidental cycle. */
    private static List<Role> expandInherited(List<Role> roles) {
        Map<UUID, Role> seen = new LinkedHashMap<>();
        Deque<Role> pending = new ArrayDeque<>(roles);
        while (!pending.isEmpty()) {
            Role role = pending.poll();
            if (role == null || seen.putIfAbsent(role.getId(), role) != null) continue;
            if (role.getParentRole() != null) pending.add(role.getParentRole());
        }
        return List.copyOf(seen.values());
    }

    // ── Menu resolution ────────────────────────────────────────────────────

    /**
     * The caller's menu. The shared catalogue comes from the version-keyed cache;
     * everything that depends on <em>who</em> is asking (roles, privileges, user
     * overrides, group membership) is read per request.
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> resolve(MenuContext ctx) {
        return metrics.observe("menu.resolve", () -> resolve(ctx, catalogueCache.get(ctx.applicationCode(), ctx.tenant().getId())),
                "application", ctx.applicationCode());
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
        Set<UUID> roleIds = effectiveRoles(ctx.user()).stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        Map<UUID, MenuAccessEffect> userOverrides = menuUserAccessRepository
                .findByUserId(ctx.user().getId()).stream()
                .filter(a -> a.isActiveAt(now))
                .collect(Collectors.toMap(a -> a.getMenuItem().getId(),
                                          MenuUserAccess::getEffect,
                                          (a, b) -> b));

        Set<UUID> groupIds = userGroupMemberRepository
                .findActiveGroupIds(ctx.user().getId(), ctx.applicationCode());

        return resolveFor(catalogue, roleIds, ctx.user().getRole() == PlatformRole.SUPERADMIN,
                userOverrides, groupIds, ctx);
    }

    /**
     * The menu a holder of one role would see, with no user-level overrides.
     * Used by the admin preview (always uncached); inherited parent roles are folded in.
     */
    @Transactional(readOnly = true)
    public List<MenuEntry> resolveForRole(Role role, MenuContext ctx) {
        Set<UUID> roleIds = expandInherited(List.of(role)).stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
        return resolveFor(catalogueCache.compileFresh(ctx.applicationCode(), ctx.tenant().getId()),
                roleIds, false, Map.of(), Set.of(), ctx);
    }

    private List<MenuEntry> resolveFor(MenuCatalogue catalogue, Set<UUID> roleIds, boolean allPrivileges,
                                       Map<UUID, MenuAccessEffect> userOverrides, Set<UUID> groupIds,
                                       MenuContext ctx) {
        Set<String> privileges = roleIds.isEmpty()
                ? Set.of()
                : rolePrivilegeRepository.findPrivilegeCodesByRoleIds(roleIds);

        Set<UUID> groupDenied = new HashSet<>();
        Set<UUID> groupGranted = new HashSet<>();
        for (UUID g : groupIds) {
            groupDenied.addAll(catalogue.groupDenied().getOrDefault(g, Set.of()));
            groupGranted.addAll(catalogue.groupGranted().getOrDefault(g, Set.of()));
        }

        MenuVisibilityPolicy.Subject subject = new MenuVisibilityPolicy.Subject(
                roleIds,
                privileges,
                allPrivileges,
                ctx.tenant().getType() == null ? null : ctx.tenant().getType().name(),
                ctx.planCode(),
                ctx.enabledFeatures(),
                userOverrides,
                catalogue.overrides(),
                groupDenied,
                groupGranted,
                Instant.now());

        Set<UUID> visible = catalogue.items().stream()
                .filter(m -> MenuVisibilityPolicy.evaluate(m, subject) == MenuVisibilityPolicy.Verdict.VISIBLE)
                .map(CatalogItem::id)
                .collect(Collectors.toSet());

        return MenuTreeBuilder.build(catalogue.items(), visible, catalogue.overrides(), ctx.locale());
    }
}
