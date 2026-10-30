package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.bootstrap.MenuEntry;
import com.flowbinary.platformservice.model.MenuItemType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the set of items a caller may see into the response tree.
 *
 * <ul>
 *   <li>Children of a hidden parent are dropped — the tree grows from visible roots.</li>
 *   <li>An item with a route of its own is a destination and stays, whether or not any of its
 *       sub-items are visible to this caller.</li>
 *   <li>A routeless item (a group) with nothing visible below it is pruned: it would be an empty
 *       heading. Temporary children and children outside navigation ({@code is_visible = false}:
 *       tabs, inner screens) never count as something below it.</li>
 *   <li>Dividers are collapsed: none leading, trailing or adjacent, and a parent holding
 *       only dividers counts as empty.</li>
 * </ul>
 */
final class MenuTreeBuilder {

    private final Map<UUID, List<CatalogItem>>   childrenByParent;
    private final Set<UUID>                       visible;
    private final Map<UUID, CatalogOverride>     overrides;
    private final String                          locale;
    private final Comparator<CatalogItem>        byOrder;

    private MenuTreeBuilder(List<CatalogItem> all, Set<UUID> visible,
                            Map<UUID, CatalogOverride> overrides, String locale) {
        this.childrenByParent = all.stream()
                .filter(m -> m.parentId() != null)
                .collect(Collectors.groupingBy(CatalogItem::parentId));
        this.visible   = visible;
        this.overrides = overrides;
        this.locale    = locale;
        this.byOrder   = Comparator.<CatalogItem>comparingInt(this::effectiveSortOrder)
                .thenComparing(Comparator.comparingInt(CatalogItem::priority).reversed())
                .thenComparing(CatalogItem::code);
    }

    static List<MenuEntry> build(List<CatalogItem> all, Set<UUID> visible,
                                 Map<UUID, CatalogOverride> overrides, String locale) {
        MenuTreeBuilder b = new MenuTreeBuilder(all, visible, overrides, locale);
        List<MenuEntry> roots = all.stream()
                .filter(m -> m.parentId() == null && visible.contains(m.id()))
                .sorted(b.byOrder)
                .map(b::toEntry)
                .filter(Objects::nonNull)
                .toList();
        return tidyDividers(roots);
    }

    private MenuEntry toEntry(CatalogItem item) {
        List<CatalogItem> defined = childrenByParent.getOrDefault(item.id(), List.of());

        List<MenuEntry> children = tidyDividers(defined.stream()
                .filter(c -> visible.contains(c.id()))
                .sorted(byOrder)
                .map(this::toEntry)
                .filter(Objects::nonNull)
                .toList());

        if (item.route() == null && children.isEmpty()) {
            boolean hasChildren = defined.stream().anyMatch(c -> !c.temporary() && c.visible());
            if (hasChildren || item.type() == MenuItemType.GROUP) return null;
        }

        CatalogOverride override = overrides.get(item.id());
        String label = override != null && override.label() != null
                ? override.label() : item.labelFor(locale);
        String icon = override != null && override.icon() != null
                ? override.icon() : item.icon();

        return MenuEntry.builder()
                .id(item.code())
                .label(label)
                .icon(icon)
                .path(item.route())
                .feature(item.requiredFeature())
                .type(item.type().name())
                .behavior(item.behavior().name())
                .target(item.target().name())
                .placement(item.placement().name())
                .order(effectiveSortOrder(item))
                .separatorBefore(item.separatorBefore() ? Boolean.TRUE : null)
                .badge(item.badge())
                .expiresAt(item.temporary() ? item.endsAt() : null)
                .children(children.isEmpty() ? null : children)
                .build();
    }

    /**
     * The subtrees of one placement, taken from a resolved tree: every entry of that
     * placement whose parent is of another placement (or which is a root), with its
     * children as resolved. Pruning has already run on the whole tree, so a settings
     * page is only returned while the settings hub that holds it is visible.
     */
    static List<MenuEntry> forPlacement(List<MenuEntry> tree, String placement) {
        List<MenuEntry> out = new ArrayList<>();
        collect(tree, placement, out);
        return tidyDividers(out);
    }

    private static void collect(List<MenuEntry> entries, String placement, List<MenuEntry> out) {
        if (entries == null) return;
        for (MenuEntry e : entries) {
            if (placement.equals(e.getPlacement())) out.add(e);
            else collect(e.getChildren(), placement, out);
        }
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

    private int effectiveSortOrder(CatalogItem item) {
        CatalogOverride o = overrides.get(item.id());
        return o != null && o.sortOrder() != null ? o.sortOrder() : item.sortOrder();
    }
}
