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
 *   <li>it is live in the requested application — active, visible, not soft-deleted;</li>
 *   <li>the tenant's plan is in {@code menu_plan_access};</li>
 *   <li>its {@code required_feature} is null or enabled for that plan;</li>
 *   <li>its {@code required_privilege} is null or held by the user;</li>
 *   <li>some role the user holds grants it, or the user is granted it directly;</li>
 *   <li>the user has no explicit DENY on it — a deny always wins;</li>
 *   <li>the tenant has not hidden it.</li>
 * </ol>
 * Surviving items are then relabelled and reordered by any tenant override,
 * and localised to the caller's locale.
 */
@Service
@RequiredArgsConstructor
public class MenuResolutionService {

    /** Application a caller resolves against when it does not name one. */
    public static final String DEFAULT_APPLICATION = "PROVIDER_CONSOLE";

    private final MenuItemRepository            menuItemRepository;
    private final MenuUserAccessRepository      menuUserAccessRepository;
    private final MenuTenantOverrideRepository  menuTenantOverrideRepository;
    private final UserRoleAssignmentRepository  userRoleAssignmentRepository;
    private final RolePrivilegeRepository       rolePrivilegeRepository;
    private final RoleRepository                roleRepository;

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

    @Transactional(readOnly = true)
    public List<MenuEntry> resolve(MenuContext ctx) {
        Set<UUID> roleIds = effectiveRoles(ctx.user()).stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        Set<String> privileges = roleIds.isEmpty()
                ? Set.of()
                : rolePrivilegeRepository.findPrivilegeCodesByRoleIds(roleIds);
        boolean allPrivileges = ctx.user().getRole() == PlatformRole.SUPERADMIN;

        Instant now = Instant.now();
        Map<UUID, MenuAccessEffect> userOverrides = menuUserAccessRepository
                .findByUserId(ctx.user().getId()).stream()
                .filter(a -> a.isActiveAt(now))
                .collect(Collectors.toMap(a -> a.getMenuItem().getId(),
                                          MenuUserAccess::getEffect,
                                          (a, b) -> b));

        Map<UUID, MenuTenantOverride> tenantOverrides = menuTenantOverrideRepository
                .findByTenantId(ctx.tenant().getId()).stream()
                .collect(Collectors.toMap(o -> o.getMenuItem().getId(), o -> o, (a, b) -> b));

        List<MenuItem> all = menuItemRepository.findLiveByApplicationCode(ctx.applicationCode());

        Set<UUID> visible = all.stream()
                .filter(m -> isVisible(m, roleIds, privileges, allPrivileges,
                                       userOverrides, tenantOverrides, ctx))
                .map(MenuItem::getId)
                .collect(Collectors.toSet());

        Comparator<MenuItem> byOrder =
                Comparator.comparingInt(m -> effectiveSortOrder(m, tenantOverrides));

        return all.stream()
                .filter(m -> m.getParent() == null && visible.contains(m.getId()))
                .sorted(byOrder)
                .map(m -> toEntry(m, visible, tenantOverrides, byOrder, ctx.locale()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isVisible(MenuItem item,
                                     Set<UUID> roleIds,
                                     Set<String> privileges,
                                     boolean allPrivileges,
                                     Map<UUID, MenuAccessEffect> userOverrides,
                                     Map<UUID, MenuTenantOverride> tenantOverrides,
                                     MenuContext ctx) {

        MenuAccessEffect effect = userOverrides.get(item.getId());
        if (effect == MenuAccessEffect.DENY) return false;

        MenuTenantOverride override = tenantOverrides.get(item.getId());
        if (override != null && Boolean.TRUE.equals(override.getIsHidden())) return false;

        if (!Boolean.TRUE.equals(item.getIsVisible())) return false;

        boolean planAllowed = item.getPlanAccess().stream()
                .anyMatch(pa -> pa.getId().getPlan().name().equalsIgnoreCase(ctx.planCode()));
        if (!planAllowed) return false;

        String feature = item.getRequiredFeature();
        if (feature != null && !ctx.enabledFeatures().contains(feature)) return false;

        Privilege required = item.getRequiredPrivilege();
        if (required != null && !allPrivileges && !privileges.contains(required.getCode())) {
            return false;
        }

        if (allPrivileges || effect == MenuAccessEffect.GRANT) return true;

        // Compare on the FK in the composite id — no need to load each Role.
        return item.getRoleAccess().stream()
                .anyMatch(ra -> roleIds.contains(ra.getId().getRoleId()));
    }

    private static MenuEntry toEntry(MenuItem item,
                                     Set<UUID> visible,
                                     Map<UUID, MenuTenantOverride> overrides,
                                     Comparator<MenuItem> byOrder,
                                     String locale) {

        List<MenuEntry> children = item.getChildren().stream()
                .filter(c -> visible.contains(c.getId()))
                .sorted(byOrder)
                .map(c -> toEntry(c, visible, overrides, byOrder, locale))
                .filter(Objects::nonNull)
                .toList();

        // A group that leads nowhere and has nothing left under it is noise.
        if (children.isEmpty() && item.getRoute() == null && item.getType() == MenuItemType.GROUP) {
            return null;
        }

        MenuTenantOverride override = overrides.get(item.getId());
        String label = override != null && override.getLabel() != null
                ? override.getLabel()
                : item.labelFor(locale);
        String icon = override != null && override.getIcon() != null
                ? override.getIcon()
                : item.getIcon();

        List<String> roleCodes = item.getRoleAccess().stream()
                .map(ra -> ra.getRole().getCode())
                .sorted()
                .toList();

        return MenuEntry.builder()
                .id(item.getCode())
                .label(label)
                .icon(icon)
                .path(item.getRoute())
                .roles(roleCodes.isEmpty() ? null : roleCodes)
                .feature(item.getRequiredFeature())
                .children(children.isEmpty() ? null : children)
                .build();
    }

    private static int effectiveSortOrder(MenuItem item, Map<UUID, MenuTenantOverride> overrides) {
        MenuTenantOverride override = overrides.get(item.getId());
        if (override != null && override.getSortOrder() != null) return override.getSortOrder();
        return item.getSortOrder();
    }
}
