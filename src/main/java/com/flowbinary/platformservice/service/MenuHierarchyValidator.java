package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.exception.MenuHierarchyException;
import com.flowbinary.platformservice.exception.MenuHierarchyException.Reason;
import com.flowbinary.platformservice.model.MenuItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Guards the menu tree invariants on every structural write: no cycles, no
 * orphans, no cross-application or cross-tenant parents, no duplicate codes,
 * and a bounded depth. Also owns the materialised {@code depth} / {@code path}
 * columns, so moving a subtree keeps every descendant consistent.
 *
 * <p>Operates on the item list the caller passes in, so it needs no repository
 * and is unit-testable. Items not yet persisted have no id; they are tracked by
 * identity (see {@link #key}).
 */
@Component
public class MenuHierarchyValidator {

    private final int maxDepth;

    @Autowired
    public MenuHierarchyValidator(AppProperties properties) {
        this(properties.getMenu().getMaxDepth());
    }

    public MenuHierarchyValidator(int maxDepth) {
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be >= 1");
        this.maxDepth = maxDepth;
    }

    public int maxDepth() {
        return maxDepth;
    }

    /**
     * Checks that {@code item} can sit under {@code newParent} (null = root),
     * then writes the new parent, depth and path to it and to every descendant.
     * Nothing is mutated unless every check passes.
     *
     * @param all every item visible to the scope, including {@code item} if already in the tree
     */
    public void place(MenuItem item, MenuItem newParent, Collection<MenuItem> all) {
        Map<Object, List<MenuItem>> children = childrenByParent(all);

        if (newParent != null) {
            if (key(newParent).equals(key(item))) {
                throw new MenuHierarchyException(Reason.SELF_PARENT, "A menu item cannot be its own parent");
            }
            if (!sameApplication(item, newParent)) {
                throw new MenuHierarchyException(Reason.CROSS_APPLICATION,
                        "Parent '" + newParent.getCode() + "' belongs to a different application");
            }
            if (newParent.getTenant() != null && !sameTenant(item, newParent)) {
                throw new MenuHierarchyException(Reason.CROSS_TENANT,
                        "Parent '" + newParent.getCode() + "' belongs to a different tenant");
            }
            if (item.getTenant() == null && newParent.getTenant() != null) {
                throw new MenuHierarchyException(Reason.CROSS_TENANT,
                        "A global item cannot sit under a tenant-owned item");
            }
            if (newParent.isModule() && !item.isModule() && !item.isTemporary()) {
                throw new MenuHierarchyException(Reason.MODULE_PARENT,
                        "Only a module's own items can sit under one of its items; it would take them with it when it changes its menu");
            }
            if (newParent.isTemporary()) {
                throw new MenuHierarchyException(Reason.TEMPORARY_PARENT,
                        "A temporary menu cannot have children; it would take them with it when it expires");
            }
            if (newParent.getDeletedAt() != null) {
                throw new MenuHierarchyException(Reason.PARENT_NOT_FOUND,
                        "Parent '" + newParent.getCode() + "' is deleted");
            }
            if (isWithin(newParent, item, all)) {
                throw new MenuHierarchyException(Reason.CYCLE,
                        "Moving '" + item.getCode() + "' under '" + newParent.getCode() + "' creates a cycle");
            }
        }

        int newDepth = newParent == null ? 0 : newParent.getDepth() + 1;
        int height   = subtreeHeight(key(item), children, new HashSet<>());
        if (newDepth + height >= maxDepth) {
            throw new MenuHierarchyException(Reason.MAX_DEPTH,
                    "Placing '" + item.getCode() + "' here makes the menu " + (newDepth + height + 1)
                            + " levels deep; the maximum is " + maxDepth);
        }
        assertCodeFree(item, all);

        item.setParent(newParent);
        item.setDepth(newDepth);
        item.setPath(newParent == null ? item.getCode() : newParent.getPath() + "/" + item.getCode());
        cascade(item, children);
    }

    /**
     * Audits an existing tree (e.g. after a module sync or manual SQL).
     * Returns every violation instead of failing on the first.
     */
    public List<String> audit(Collection<MenuItem> all) {
        List<String> problems = new ArrayList<>();
        Map<Object, MenuItem> byKey = new HashMap<>();
        for (MenuItem m : all) {
            if (byKey.put(key(m), m) != null) problems.add("Duplicate id " + m.getId());
        }

        Set<String> codes = new HashSet<>();
        for (MenuItem m : all) {
            if (!codes.add(m.getApplication().getId() + "|" + ownerOf(m) + "|" + m.getCode())) {
                problems.add("Duplicate code '" + m.getCode() + "' in application");
            }
            MenuItem parent = m.getParent();
            if (parent != null && !byKey.containsKey(key(parent))) {
                problems.add("Orphan '" + m.getCode() + "': parent " + parent.getId() + " not in catalogue");
            }
            if (m.getDepth() >= maxDepth) {
                problems.add("'" + m.getCode() + "' is at depth " + m.getDepth() + " (max level " + maxDepth + ")");
            }
            if (hasCycle(m, byKey)) problems.add("Cycle through '" + m.getCode() + "'");
        }
        return problems;
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** The id when persisted, otherwise the instance itself. Proxy-safe for loaded items. */
    private static Object key(MenuItem m) {
        return m.getId() != null ? m.getId() : m;
    }

    private static Object ownerOf(MenuItem m) {
        return m.getTenant() == null ? "global" : m.getTenant().getId();
    }

    private static Map<Object, List<MenuItem>> childrenByParent(Collection<MenuItem> all) {
        return all.stream()
                .filter(m -> m.getParent() != null)
                .collect(Collectors.groupingBy(m -> key(m.getParent())));
    }

    /** True when {@code candidate} is {@code ancestor} or sits somewhere beneath it. */
    private static boolean isWithin(MenuItem candidate, MenuItem ancestor, Collection<MenuItem> all) {
        Map<Object, MenuItem> byKey = new HashMap<>();
        all.forEach(m -> byKey.put(key(m), m));
        Set<Object> seen = new HashSet<>();
        for (MenuItem cur = candidate; cur != null;
             cur = cur.getParent() == null ? null : byKey.getOrDefault(key(cur.getParent()), cur.getParent())) {
            if (key(ancestor).equals(key(cur))) return true;
            if (!seen.add(key(cur))) return true; // pre-existing cycle: refuse to extend it
        }
        return false;
    }

    private static boolean hasCycle(MenuItem start, Map<Object, MenuItem> byKey) {
        Set<Object> seen = new HashSet<>();
        for (MenuItem cur = start; cur != null;
             cur = cur.getParent() == null ? null : byKey.get(key(cur.getParent()))) {
            if (!seen.add(key(cur))) return true;
        }
        return false;
    }

    /** Levels beneath {@code key}: 0 for a leaf. */
    private static int subtreeHeight(Object key, Map<Object, List<MenuItem>> children, Set<Object> seen) {
        if (!seen.add(key)) return 0;
        int max = 0;
        for (MenuItem c : children.getOrDefault(key, List.of())) {
            max = Math.max(max, 1 + subtreeHeight(key(c), children, seen));
        }
        return max;
    }

    private static void cascade(MenuItem parent, Map<Object, List<MenuItem>> children) {
        for (MenuItem c : children.getOrDefault(key(parent), List.of())) {
            c.setDepth(parent.getDepth() + 1);
            c.setPath(parent.getPath() + "/" + c.getCode());
            cascade(c, children);
        }
    }

    private static boolean sameApplication(MenuItem a, MenuItem b) {
        return a.getApplication() != null && b.getApplication() != null
                && Objects.equals(a.getApplication().getId(), b.getApplication().getId());
    }

    private static boolean sameTenant(MenuItem a, MenuItem b) {
        return Objects.equals(ownerOf(a), ownerOf(b));
    }

    private static void assertCodeFree(MenuItem item, Collection<MenuItem> all) {
        boolean taken = all.stream().anyMatch(m ->
                !key(m).equals(key(item))
                        && m.getDeletedAt() == null
                        && m.getCode().equals(item.getCode())
                        && sameApplication(m, item));
        if (taken) {
            throw new MenuHierarchyException(Reason.DUPLICATE_CODE,
                    "Menu code '" + item.getCode() + "' already exists in this application");
        }
    }
}
