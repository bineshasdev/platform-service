package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Canonical, comparable picture of what one scope owns, as a flat map of
 * "atoms". Snapshots store it; publish audits and rollback are computed as the
 * difference between two of them.
 *
 * <pre>
 *   item:&lt;code&gt;              an item the scope owns, with its plans
 *   moditem:&lt;code&gt;          a module item's admin-controlled part (enabled, sortOrder); global scope only
 *   override:&lt;code&gt;          tenant relabel/reorder/hide of a platform item
 *   user:&lt;code&gt;:&lt;userId&gt;    one user's GRANT/DENY on an item
 * </pre>
 * Values are plain maps/lists/strings so they round-trip through jsonb and
 * compare with {@code equals}.
 *
 * <p>Snapshots taken before roles and groups left the menu model also hold
 * {@code grants:} and {@code group:} atoms and a {@code roles} field on items.
 * {@link #diff} ignores them, so such a snapshot can still be compared and
 * rolled back to.
 */
public final class MenuScopeState {

    public enum Kind { CREATE, UPDATE, DELETE }

    public record Change(String key, Kind kind, Map<String, Object> before, Map<String, Object> after) {
    }

    private MenuScopeState() {
    }

    // ── Reading ────────────────────────────────────────────────────────────

    public static Map<String, Object> capture(MenuScope scope,
                                              Collection<MenuItem> items,
                                              Collection<MenuTenantOverride> overrides,
                                              Collection<MenuUserAccess> userAccess) {
        Map<String, Object> state = new TreeMap<>();
        UUID tenantId = scope.tenantId();

        for (MenuItem m : items) {
            if (m.getDeletedAt() != null || m.isTemporary()) continue;   // temporary menus are not drafted
            if (m.isModule()) {                                           // content is the module's; admins own hide + order
                if (scope.isGlobal()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("enabled", Boolean.TRUE.equals(m.getIsActive()));
                    a.put("sortOrder", m.getSortOrder());
                    state.put("moditem:" + m.getCode(), a);
                }
                continue;
            }
            UUID owner = m.getTenant() == null ? null : m.getTenant().getId();
            if (Objects.equals(owner, tenantId)) {
                state.put("item:" + m.getCode(), itemAtom(m));
            }
        }
        for (MenuTenantOverride o : overrides) {
            if (o.getMenuItem().isTemporary()) continue;
            Map<String, Object> atom = new LinkedHashMap<>();
            atom.put("label", o.getLabel());
            atom.put("icon", o.getIcon());
            atom.put("sortOrder", o.getSortOrder());
            atom.put("hidden", Boolean.TRUE.equals(o.getIsHidden()));
            state.put("override:" + o.getMenuItem().getCode(), atom);
        }
        for (MenuUserAccess a : userAccess) {
            if (a.getMenuItem().isTemporary()) continue;
            Map<String, Object> atom = new LinkedHashMap<>();
            atom.put("effect", a.getEffect().name());
            atom.put("reason", a.getReason());
            atom.put("validTo", a.getValidTo() == null ? null : a.getValidTo().toString());
            state.put("user:" + a.getMenuItem().getCode() + ":" + a.getUser().getId(), atom);
        }
        return state;
    }

    private static Map<String, Object> itemAtom(MenuItem m) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("parentCode", m.getParent() == null ? null : m.getParent().getCode());
        a.put("label", m.getLabel());
        a.put("description", m.getDescription());
        a.put("icon", m.getIcon());
        a.put("route", m.getRoute());
        a.put("type", m.getType().name());
        a.put("behavior", m.getBehavior().name());
        a.put("target", m.getTarget().name());
        a.put("placement", m.getPlacement().name());
        a.put("separatorBefore", Boolean.TRUE.equals(m.getSeparatorBefore()));
        a.put("visible", Boolean.TRUE.equals(m.getIsVisible()));
        a.put("enabled", Boolean.TRUE.equals(m.getIsActive()));
        a.put("sortOrder", m.getSortOrder());
        a.put("requiredPrivilege", m.getRequiredPrivilege() == null ? null : m.getRequiredPrivilege().getCode());
        a.put("requiredFeature", m.getRequiredFeature());
        a.put("plans", m.getPlanAccess().stream().map(p -> p.getId().getPlan().name()).sorted().toList());
        a.put("tenantTypes", m.getTenantTypes() == null ? null : m.getTenantTypes().stream().sorted().toList());
        return a;
    }

    // ── Diff ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static List<Change> diff(Map<String, Object> before, Map<String, Object> after) {
        List<Change> out = new ArrayList<>();
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            if (isLegacy(k)) continue;
            Map<String, Object> b = (Map<String, Object>) before.get(k);
            Map<String, Object> a = (Map<String, Object>) after.get(k);
            if (b == null) out.add(new Change(k, Kind.CREATE, null, a));
            else if (a == null) out.add(new Change(k, Kind.DELETE, b, null));
            else if (!normalise(b).equals(normalise(a))) out.add(new Change(k, Kind.UPDATE, b, a));
        }
        return out;
    }

    /** Atoms from the retired role and group assignments. */
    private static boolean isLegacy(String key) {
        return key.startsWith("grants:") || key.startsWith("group:");
    }

    /** jsonb round-trips can turn Integer into Long or List into ArrayList; compare on strings. */
    private static Map<String, String> normalise(Map<String, Object> atom) {
        return atom.entrySet().stream().filter(e -> !"roles".equals(e.getKey())).collect(Collectors.toMap(
                Map.Entry::getKey, e -> String.valueOf(e.getValue()), (x, y) -> x, TreeMap::new));
    }

    // ── Reverse operations (rollback) ──────────────────────────────────────

    /**
     * The operations that turn the current state into {@code target}. Item
     * creates/updates run parents-first, deletes children-first.
     */
    @SuppressWarnings("unchecked")
    public static List<MenuOp> opsToReach(Map<String, Object> current, Map<String, Object> target) {
        List<Change> changes = diff(current, target);
        Map<String, Integer> depth = new HashMap<>();
        for (Map.Entry<String, Object> e : target.entrySet()) {
            if (e.getKey().startsWith("item:")) depth.put(e.getKey().substring(5), depthOf(e.getKey().substring(5), target));
        }
        for (Map.Entry<String, Object> e : current.entrySet()) {
            if (e.getKey().startsWith("item:")) {
                depth.putIfAbsent(e.getKey().substring(5), depthOf(e.getKey().substring(5), current));
            }
        }

        List<MenuOp> puts = new ArrayList<>(), deletes = new ArrayList<>(), rest = new ArrayList<>();
        List<Change> itemPuts = changes.stream().filter(c -> c.key().startsWith("item:") && c.kind() != Kind.DELETE)
                .sorted(Comparator.comparingInt(c -> depth.getOrDefault(c.key().substring(5), 0))).toList();
        List<Change> itemDeletes = changes.stream().filter(c -> c.key().startsWith("item:") && c.kind() == Kind.DELETE)
                .sorted(Comparator.comparingInt((Change c) -> depth.getOrDefault(c.key().substring(5), 0)).reversed()).toList();

        for (Change c : itemPuts) puts.add(putFrom(c.key().substring(5), c.after()));
        for (Change c : itemDeletes) {
            deletes.add(MenuOp.builder().op(MenuOpType.DELETE_ITEM).code(c.key().substring(5)).build());
        }

        for (Change c : changes) {
            String k = c.key();
            Map<String, Object> a = c.after();
            if (k.startsWith("moditem:")) {
                if (c.kind() == Kind.UPDATE) {         // a module item that came or went is the module's doing, not a draft's
                    rest.add(MenuOp.builder().op(MenuOpType.PATCH_ITEM).code(k.substring(8))
                            .enabled((Boolean) a.get("enabled")).sortOrder(number(a.get("sortOrder"))).build());
                }
            } else if (k.startsWith("override:")) {
                rest.add(MenuOp.builder().op(MenuOpType.SET_OVERRIDE).code(k.substring(9))
                        .label(a == null ? null : (String) a.get("label"))
                        .icon(a == null ? null : (String) a.get("icon"))
                        .sortOrder(a == null ? null : number(a.get("sortOrder")))
                        .hidden(a != null && Boolean.TRUE.equals(a.get("hidden")))
                        .build());
            } else if (k.startsWith("user:")) {
                String[] p = k.split(":", 3);
                rest.add(MenuOp.builder().op(MenuOpType.SET_USER_ACCESS).code(p[1]).userId(UUID.fromString(p[2]))
                        .effect(a == null ? null : (String) a.get("effect"))
                        .reason(a == null ? null : (String) a.get("reason"))
                        .validTo(a == null || a.get("validTo") == null ? null : java.time.Instant.parse((String) a.get("validTo")))
                        .build());
            }
        }
        List<MenuOp> all = new ArrayList<>(puts);
        all.addAll(deletes);
        all.addAll(rest);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static MenuOp putFrom(String code, Map<String, Object> a) {
        return MenuOp.builder().op(MenuOpType.PUT_ITEM).code(code)
                .parentCode((String) a.get("parentCode"))
                .sortOrder(number(a.get("sortOrder")))
                .label((String) a.get("label")).description((String) a.get("description"))
                .icon((String) a.get("icon")).route((String) a.get("route"))
                .type((String) a.get("type")).behavior((String) a.get("behavior")).target((String) a.get("target"))
                .placement((String) a.get("placement"))
                .tenantTypes(a.get("tenantTypes") == null ? Set.of() : new TreeSet<>((List<String>) a.get("tenantTypes")))
                .separatorBefore((Boolean) a.get("separatorBefore")).visible((Boolean) a.get("visible"))
                .enabled((Boolean) a.get("enabled"))
                .requiredPrivilege((String) a.get("requiredPrivilege")).requiredFeature((String) a.get("requiredFeature"))
                .plans(new TreeSet<>((List<String>) a.get("plans")))
                .build();
    }

    private static Integer number(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    @SuppressWarnings("unchecked")
    private static int depthOf(String code, Map<String, Object> state) {
        int d = 0;
        Set<String> seen = new HashSet<>();
        Map<String, Object> cur = (Map<String, Object>) state.get("item:" + code);
        while (cur != null && cur.get("parentCode") != null && seen.add((String) cur.get("parentCode"))) {
            d++;
            cur = (Map<String, Object>) state.get("item:" + cur.get("parentCode"));
        }
        return d;
    }
}
