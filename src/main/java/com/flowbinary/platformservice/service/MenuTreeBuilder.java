package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.MenuItem;
import com.flowbinary.platformservice.model.MenuItemType;
import com.flowbinary.platformservice.model.MenuTenantOverride;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the set of items a caller may see into the response tree.
 *
 * <ul>
 *   <li>Children of a hidden parent are dropped — the tree grows from visible roots.</li>
 *   <li>A parent that has children in the catalogue but none left for this caller is
 *       pruned, even if it has a route of its own. A leaf is never pruned for that reason.</li>
 *   <li>A childless {@code GROUP} with no route is pruned.</li>
 *   <li>Dividers are collapsed: none leading, trailing or adjacent, and a parent holding
 *       only dividers counts as empty.</li>
 * </ul>
 */
final class MenuTreeBuilder {

    private final Map<UUID, List<MenuItem>>       childrenByParent;
    private final Set<UUID>                       visible;
    private final Map<UUID, MenuTenantOverride>   overrides;
    private final String                          locale;
    private final Comparator<MenuItem>            byOrder;

    private MenuTreeBuilder(List<MenuItem> all, Set<UUID> visible,
                            Map<UUID, MenuTenantOverride> overrides, String locale) {
        this.childrenByParent = all.stream()
                .filter(m -> m.getParent() != null)
                .collect(Collectors.groupingBy(m -> m.getParent().getId()));
        this.visible   = visible;
        this.overrides = overrides;
        this.locale    = locale;
        this.byOrder   = Comparator.<MenuItem>comparingInt(this::effectiveSortOrder)
                .thenComparing(MenuItem::getCode);
    }

    static List<MenuEntry> build(List<MenuItem> all, Set<UUID> visible,
                                 Map<UUID, MenuTenantOverride> overrides, String locale) {
        MenuTreeBuilder b = new MenuTreeBuilder(all, visible, overrides, locale);
        List<MenuEntry> roots = all.stream()
                .filter(m -> m.getParent() == null && visible.contains(m.getId()))
                .sorted(b.byOrder)
                .map(b::toEntry)
                .filter(Objects::nonNull)
                .toList();
        return tidyDividers(roots);
    }

    private MenuEntry toEntry(MenuItem item) {
        List<MenuItem> defined = childrenByParent.getOrDefault(item.getId(), List.of());

        List<MenuEntry> children = tidyDividers(defined.stream()
                .filter(c -> visible.contains(c.getId()))
                .sorted(byOrder)
                .map(this::toEntry)
                .filter(Objects::nonNull)
                .toList());

        boolean hasChildren = !defined.isEmpty();
        if (hasChildren && children.isEmpty()) return null;
        if (!hasChildren && item.getType() == MenuItemType.GROUP && item.getRoute() == null) return null;

        MenuTenantOverride override = overrides.get(item.getId());
        String label = override != null && override.getLabel() != null
                ? override.getLabel() : item.labelFor(locale);
        String icon = override != null && override.getIcon() != null
                ? override.getIcon() : item.getIcon();

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
                .type(item.getType().name())
                .behavior(item.getBehavior().name())
                .target(item.getTarget().name())
                .order(effectiveSortOrder(item))
                .separatorBefore(Boolean.TRUE.equals(item.getSeparatorBefore()) ? Boolean.TRUE : null)
                .children(children.isEmpty() ? null : children)
                .build();
    }

    /** Drops leading, trailing and consecutive dividers. */
    private static List<MenuEntry> tidyDividers(List<MenuEntry> entries) {
        List<MenuEntry> out = new ArrayList<>(entries.size());
        for (MenuEntry e : entries) {
            if (isDivider(e) && (out.isEmpty() || isDivider(out.get(out.size() - 1)))) continue;
            out.add(e);
        }
        while (!out.isEmpty() && isDivider(out.get(out.size() - 1))) out.remove(out.size() - 1);
        return List.copyOf(out);
    }

    private static boolean isDivider(MenuEntry e) {
        return MenuItemType.DIVIDER.name().equals(e.getType());
    }

    private int effectiveSortOrder(MenuItem item) {
        MenuTenantOverride o = overrides.get(item.getId());
        return o != null && o.getSortOrder() != null ? o.getSortOrder() : item.getSortOrder();
    }
}
