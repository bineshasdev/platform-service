package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.exception.MenuHierarchyException;
import com.flowbinary.platformservice.exception.MenuHierarchyException.Reason;
import com.flowbinary.platformservice.model.MenuItem;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Guards the menu tree invariants on every structural write: no cycles, no
 * orphans, no cross-application parents, no duplicate codes, and a bounded
 * depth. Also owns the materialised {@code depth} / {@code path} columns, so
 * moving a subtree keeps every descendant consistent.
 *
 * <p>Operates on the full item list of one application, passed in by the
 * caller, so it needs no repository and is unit-testable.
 */
@Component
public class MenuHierarchyValidator {

    private final int maxDepth;

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
     * @param all every item of the application, including {@code item} if it is already persisted
     */
    public void place(MenuItem item, MenuItem newParent, Collection<MenuItem> all) {
        Map<UUID, List<MenuItem>> children = childrenByParent(all);

        if (newParent != null) {
            if (newParent.getId() != null && newParent.getId().equals(item.getId())) {
                throw new MenuHierarchyException(Reason.SELF_PARENT, "A menu item cannot be its own parent");
            }
            if (!sameApplication(item, newParent)) {
                throw new MenuHierarchyException(Reason.CROSS_APPLICATION,
                        "Parent '" + newParent.getCode() + "' belongs to a different application");
            }
            if (newParent.getDeletedAt() != null) {
                throw new MenuHierarchyException(Reason.PARENT_NOT_FOUND,
                        "Parent '" + newParent.getCode() + "' is deleted");
            }
            if (item.getId() != null && isDescendant(newParent, item.getId(), all)) {
                throw new MenuHierarchyException(Reason.CYCLE,
                        "Moving '" + item.getCode() + "' under '" + newParent.getCode() + "' creates a cycle");
            }
        }

        int newDepth = newParent == null ? 0 : newParent.getDepth() + 1;
        int height   = item.getId() == null ? 0 : subtreeHeight(item.getId(), children);
        if (newDepth + height >= maxDepth) {
            throw new MenuHierarchyException(Reason.MAX_DEPTH,
                    "Placing '" + item.getCode() + "' here makes the menu " + (newDepth + height + 1)
                            + " levels deep; the maximum is " + maxDepth);
        }
        assertCodeFree(item, all);

        item.setParent(newParent);
        item.setDepth(newDepth);
        item.setPath(newParent == null ? item.getCode() : newParent.getPath() + "/" + item.getCode());
        if (item.getId() != null) cascade(item, children);
    }

    /**
     * Audits an existing tree (e.g. after a module sync or manual SQL).
     * Returns every violation instead of failing on the first.
     */
    public List<String> audit(Collection<MenuItem> all) {
        List<String> problems = new ArrayList<>();
        Map<UUID, MenuItem> byId = new HashMap<>();
        for (MenuItem m : all) {
            if (byId.put(m.getId(), m) != null) problems.add("Duplicate id " + m.getId());
        }

        Set<String> codes = new HashSet<>();
        for (MenuItem m : all) {
            if (!codes.add(m.getApplication().getId() + "|" + m.getCode())) {
                problems.add("Duplicate code '" + m.getCode() + "' in application");
            }
            MenuItem parent = m.getParent();
            if (parent != null && !byId.containsKey(parent.getId())) {
                problems.add("Orphan '" + m.getCode() + "': parent " + parent.getId() + " not in catalogue");
            }
            if (m.getDepth() >= maxDepth) {
                problems.add("'" + m.getCode() + "' is at depth " + m.getDepth() + " (max level " + maxDepth + ")");
            }
            if (hasCycle(m, byId)) problems.add("Cycle through '" + m.getCode() + "'");
        }
        return problems;
    }

    // ── internals ──────────────────────────────────────────────────────────

    private static Map<UUID, List<MenuItem>> childrenByParent(Collection<MenuItem> all) {
        return all.stream()
                .filter(m -> m.getParent() != null)
                .collect(Collectors.groupingBy(m -> m.getParent().getId()));
    }

    /** True when {@code candidate} is {@code ancestorId} or sits somewhere beneath it. */
    private static boolean isDescendant(MenuItem candidate, UUID ancestorId, Collection<MenuItem> all) {
        Map<UUID, MenuItem> byId = all.stream().collect(Collectors.toMap(MenuItem::getId, m -> m, (a, b) -> a));
        Set<UUID> seen = new HashSet<>();
        for (MenuItem cur = candidate; cur != null; cur = cur.getParent() == null ? null : byId.get(cur.getParent().getId())) {
            if (ancestorId.equals(cur.getId())) return true;
            if (!seen.add(cur.getId())) return true; // pre-existing cycle: refuse to extend it
        }
        return false;
    }

    private static boolean hasCycle(MenuItem start, Map<UUID, MenuItem> byId) {
        Set<UUID> seen = new HashSet<>();
        for (MenuItem cur = start; cur != null; cur = cur.getParent() == null ? null : byId.get(cur.getParent().getId())) {
            if (!seen.add(cur.getId())) return true;
        }
        return false;
    }

    /** Levels beneath {@code id}: 0 for a leaf. */
    private static int subtreeHeight(UUID id, Map<UUID, List<MenuItem>> children) {
        return subtreeHeight(id, children, new HashSet<>());
    }

    private static int subtreeHeight(UUID id, Map<UUID, List<MenuItem>> children, Set<UUID> seen) {
        if (!seen.add(id)) return 0;
        int max = 0;
        for (MenuItem c : children.getOrDefault(id, List.of())) {
            max = Math.max(max, 1 + subtreeHeight(c.getId(), children, seen));
        }
        return max;
    }

    private static void cascade(MenuItem parent, Map<UUID, List<MenuItem>> children) {
        for (MenuItem c : children.getOrDefault(parent.getId(), List.of())) {
            c.setDepth(parent.getDepth() + 1);
            c.setPath(parent.getPath() + "/" + c.getCode());
            cascade(c, children);
        }
    }

    private static boolean sameApplication(MenuItem a, MenuItem b) {
        return a.getApplication() != null && b.getApplication() != null
                && Objects.equals(a.getApplication().getId(), b.getApplication().getId());
    }

    private static void assertCodeFree(MenuItem item, Collection<MenuItem> all) {
        boolean taken = all.stream().anyMatch(m ->
                !Objects.equals(m.getId(), item.getId())
                        && m.getDeletedAt() == null
                        && m.getCode().equals(item.getCode())
                        && sameApplication(m, item));
        if (taken) {
            throw new MenuHierarchyException(Reason.DUPLICATE_CODE,
                    "Menu code '" + item.getCode() + "' already exists in this application");
        }
    }
}
