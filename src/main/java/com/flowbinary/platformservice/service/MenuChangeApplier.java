package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.exception.MenuHierarchyException;
import com.flowbinary.platformservice.model.*;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replays draft operations against the live menu rows of one scope. The same
 * code serves preview (applied, resolved, then rolled back) and publish
 * (applied, snapshotted, committed), so a preview cannot disagree with what
 * publishing does.
 *
 * <p>Scope rules, enforced here and not only in the API:
 * <ul>
 *   <li>a scope edits only items it owns — a tenant never changes a global row;</li>
 *   <li>on a global item a tenant may only override presentation, hide it, and
 *       grant it to <em>its own</em> custom roles and users — never a system role;</li>
 *   <li>every placement goes through {@link MenuHierarchyValidator}.</li>
 * </ul>
 * The applier mutates entities in place and records newly created and removed
 * ones on the {@link Workspace}; the caller persists and deletes them.
 */
@Component
public class MenuChangeApplier {

    /** Mutable working set for one apply run. */
    public static final class Workspace {
        public final MenuScope scope;
        public final UUID actorId;
        /** Global ∪ tenant items of the application, soft-deleted included. New items are appended. */
        public final List<MenuItem> items;
        public final List<MenuTenantOverride> overrides;
        public final List<MenuUserAccess> userAccess;
        public final List<Object> created = new ArrayList<>();
        public final List<Object> removed = new ArrayList<>();

        public Workspace(MenuScope scope, UUID actorId, List<MenuItem> items,
                         List<MenuTenantOverride> overrides, List<MenuUserAccess> userAccess) {
            this.scope = scope;
            this.actorId = actorId;
            this.items = new ArrayList<>(items);
            this.overrides = new ArrayList<>(overrides);
            this.userAccess = new ArrayList<>(userAccess);
        }

        List<MenuItem> live() {
            return items.stream().filter(m -> m.getDeletedAt() == null).collect(Collectors.toList());
        }
    }

    private final MenuReferences refs;
    private final MenuHierarchyValidator hierarchy;

    public MenuChangeApplier(MenuReferences refs, MenuHierarchyValidator hierarchy) {
        this.refs = refs;
        this.hierarchy = hierarchy;
    }

    public void apply(Workspace ws, List<MenuOp> ops) {
        for (int i = 0; i < ops.size(); i++) {
            MenuOp op = ops.get(i);
            MenuOpValidator.validate(i, op, ws.scope);
            try {
                applyOne(ws, i, op);
            } catch (MenuHierarchyException e) {
                throw new MenuDraftException(i, e.getMessage());
            }
        }
    }

    private void applyOne(Workspace ws, int i, MenuOp op) {
        switch (op.op()) {
            case PUT_ITEM        -> putItem(ws, i, op);
            case PATCH_ITEM      -> patchItem(ws, i, op);
            case MOVE_ITEM       -> moveItem(ws, i, op);
            case REORDER         -> reorder(ws, i, op);
            case SET_ENABLED     -> setEnabled(ws, i, op);
            case DELETE_ITEM     -> deleteItem(ws, i, op);
            case SET_ROLES       -> setRoles(ws, i, requireLive(ws, i, op.code()), op.roles());
            case SET_USER_ACCESS -> setUserAccess(ws, i, op);
            case SET_OVERRIDE    -> setOverride(ws, i, op);
        }
    }

    // ── Items ──────────────────────────────────────────────────────────────

    private void putItem(Workspace ws, int i, MenuOp op) {
        MenuItem item = findAny(ws, op.code());
        boolean isNew = item == null;

        if (isNew) {
            if (ws.scope.isGlobal() && refs.codeUsedByAnyTenant(ws.scope.application().getId(), op.code())) {
                throw new MenuDraftException(i, "Code '" + op.code() + "' is already used by a tenant-owned item");
            }
            item = MenuItem.builder()
                    .application(ws.scope.application())
                    .tenant(ws.scope.tenant())
                    .code(op.code())
                    .label(op.label())
                    .createdBy(ws.actorId)
                    .build();
            ws.items.add(item);
            ws.created.add(item);
        } else {
            requireOwned(ws, i, item);
            item.setDeletedAt(null);                 // PUT on a deleted code restores it
        }

        item.setLabel(op.label());
        item.setDescription(op.description());
        item.setIcon(op.icon());
        item.setRoute(op.route());
        item.setType(op.type() == null ? MenuItemType.LINK : MenuItemType.valueOf(op.type()));
        item.setTarget(op.target() == null ? MenuTarget.SELF : MenuTarget.valueOf(op.target()));
        item.setBehavior(op.behavior() != null ? MenuBehavior.valueOf(op.behavior())
                : item.getType() == MenuItemType.GROUP ? MenuBehavior.EXPAND : MenuBehavior.NAVIGATE);
        item.setSeparatorBefore(op.separatorBefore() != null && op.separatorBefore());
        item.setIsVisible(op.visible() == null || op.visible());
        item.setIsActive(op.enabled() == null || op.enabled());
        item.setSortOrder(op.sortOrder() == null ? 0 : op.sortOrder());
        item.setRequiredPrivilege(privilegeOrNull(i, op.requiredPrivilege()));
        item.setRequiredFeature(featureOrNull(i, op.requiredFeature()));
        item.setUpdatedBy(ws.actorId);

        place(ws, i, item, parentOrNull(ws, i, op.parentCode()));

        // null = unchanged on an existing item; a new item defaults to every plan and no roles.
        if (op.plans() != null || isNew) setPlans(item, op.plans());
        if (op.roles() != null) setRoles(ws, i, item, op.roles());
    }

    private void patchItem(Workspace ws, int i, MenuOp op) {
        if (op.parentCode() != null) throw new MenuDraftException(i, "Use MOVE_ITEM to change the parent");
        MenuItem item = requireOwned(ws, i, requireLive(ws, i, op.code()));

        if (op.label() != null)           item.setLabel(op.label());
        if (op.description() != null)     item.setDescription(op.description());
        if (op.icon() != null)            item.setIcon(op.icon());
        if (op.route() != null)           item.setRoute(op.route());
        if (op.type() != null)            item.setType(MenuItemType.valueOf(op.type()));
        if (op.behavior() != null)        item.setBehavior(MenuBehavior.valueOf(op.behavior()));
        if (op.target() != null)          item.setTarget(MenuTarget.valueOf(op.target()));
        if (op.separatorBefore() != null) item.setSeparatorBefore(op.separatorBefore());
        if (op.visible() != null)         item.setIsVisible(op.visible());
        if (op.enabled() != null)         item.setIsActive(op.enabled());
        if (op.sortOrder() != null)       item.setSortOrder(op.sortOrder());
        if (op.requiredPrivilege() != null) item.setRequiredPrivilege(privilegeOrNull(i, op.requiredPrivilege()));
        if (op.requiredFeature() != null)   item.setRequiredFeature(featureOrNull(i, op.requiredFeature()));
        item.setUpdatedBy(ws.actorId);
    }

    private void moveItem(Workspace ws, int i, MenuOp op) {
        MenuItem item = requireOwned(ws, i, requireLive(ws, i, op.code()));
        place(ws, i, item, parentOrNull(ws, i, op.parentCode()));
        if (op.sortOrder() != null) item.setSortOrder(op.sortOrder());
        item.setUpdatedBy(ws.actorId);
    }

    private void reorder(Workspace ws, int i, MenuOp op) {
        MenuItem parent = parentOrNull(ws, i, op.parentCode());
        int position = 10;
        for (String code : op.orderedCodes()) {
            MenuItem item = requireLive(ws, i, code);
            if (!Objects.equals(parent == null ? null : keyOf(parent),
                                item.getParent() == null ? null : keyOf(item.getParent()))) {
                throw new MenuDraftException(i, "'" + code + "' is not a child of "
                        + (op.parentCode() == null ? "the root" : "'" + op.parentCode() + "'"));
            }
            if (owned(ws, item)) {
                item.setSortOrder(position);
                item.setUpdatedBy(ws.actorId);
            } else {
                MenuTenantOverride o = overrideFor(ws, item);
                o.setSortOrder(position);
                tidyOverride(ws, o);
            }
            position += 10;
        }
    }

    private void setEnabled(Workspace ws, int i, MenuOp op) {
        MenuItem item = requireLive(ws, i, op.code());
        if (owned(ws, item)) {
            item.setIsActive(op.enabled());
            item.setUpdatedBy(ws.actorId);
        } else {
            MenuTenantOverride o = overrideFor(ws, item);
            o.setIsHidden(!op.enabled());
            tidyOverride(ws, o);
        }
    }

    private void deleteItem(Workspace ws, int i, MenuOp op) {
        MenuItem item = requireOwned(ws, i, requireLive(ws, i, op.code()));
        List<MenuItem> subtree = subtree(ws, item);

        for (MenuItem m : subtree) {
            if (!owned(ws, m)) {
                throw new MenuDraftException(i, "'" + m.getCode() + "' under '" + op.code() + "' is not owned by this scope");
            }
        }
        List<UUID> ids = subtree.stream().map(MenuItem::getId).filter(Objects::nonNull).toList();
        if (ws.scope.isGlobal() && refs.hasTenantOwnedChildren(ids)) {
            throw new MenuDraftException(i, "A tenant has menu items under '" + op.code()
                    + "'; ask them to move or remove those first");
        }
        Instant now = Instant.now();
        for (MenuItem m : subtree) {
            m.setDeletedAt(now);
            m.setUpdatedBy(ws.actorId);
        }
    }

    // ── Assignments ────────────────────────────────────────────────────────

    private void setRoles(Workspace ws, int i, MenuItem item, Set<String> codes) {
        boolean owned = owned(ws, item);

        Map<UUID, Role> wanted = new LinkedHashMap<>();
        for (String code : codes) {
            Role role = refs.assignableRole(ws.scope, code)
                    .orElseThrow(() -> new MenuDraftException(i, "Unknown or unassignable role '" + code + "'"));
            if (!owned && role.getTenant() == null) {
                throw new MenuDraftException(i, "System roles on a platform item are managed by platform operators; "
                        + "use one of your own roles or a user override for '" + code + "'");
            }
            wanted.put(role.getId(), role);
        }

        // Only the roles this scope manages are replaced; a tenant leaves system-role grants alone.
        item.getRoleAccess().removeIf(ra -> manages(ws, owned, ra.getRole()) && !wanted.containsKey(ra.getRole().getId()));
        Set<UUID> present = item.getRoleAccess().stream().map(ra -> ra.getRole().getId()).collect(Collectors.toSet());
        for (Role role : wanted.values()) {
            if (present.contains(role.getId())) continue;
            item.getRoleAccess().add(MenuRoleAccess.builder()
                    .id(new MenuRoleAccessId(item.getId(), role.getId()))
                    .menuItem(item)
                    .role(role)
                    .assignedBy(ws.actorId)
                    .build());
        }
    }

    private static boolean manages(Workspace ws, boolean ownedItem, Role role) {
        if (ws.scope.isGlobal()) return role.getTenant() == null;
        if (ownedItem) return true;
        return role.getTenant() != null && Objects.equals(role.getTenant().getId(), ws.scope.tenantId());
    }

    private void setPlans(MenuItem item, Set<String> plans) {
        Set<TenantPlan> wanted = plans == null
                ? EnumSet.allOf(TenantPlan.class)
                : plans.stream().map(TenantPlan::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(TenantPlan.class)));
        item.getPlanAccess().removeIf(pa -> !wanted.contains(pa.getId().getPlan()));
        Set<TenantPlan> present = item.getPlanAccess().stream().map(pa -> pa.getId().getPlan()).collect(Collectors.toSet());
        for (TenantPlan p : wanted) {
            if (present.contains(p)) continue;
            item.getPlanAccess().add(MenuPlanAccess.builder()
                    .id(new MenuPlanAccessId(item.getId(), p))
                    .menuItem(item)
                    .build());
        }
    }

    private void setUserAccess(Workspace ws, int i, MenuOp op) {
        MenuItem item = requireLive(ws, i, op.code());
        TenantUser user = refs.tenantUser(ws.scope.tenantId(), op.userId())
                .orElseThrow(() -> new MenuDraftException(i, "User not found in this tenant"));

        MenuUserAccess existing = ws.userAccess.stream()
                .filter(a -> sameItem(a.getMenuItem(), item) && a.getUser().getId().equals(user.getId()))
                .findFirst().orElse(null);

        if (op.effect() == null) {
            if (existing != null) {
                ws.userAccess.remove(existing);
                ws.removed.add(existing);
            }
            return;
        }
        if (op.validTo() != null && !op.validTo().isAfter(Instant.now())) {
            throw new MenuDraftException(i, "validTo must be in the future");
        }
        MenuAccessEffect effect = MenuAccessEffect.valueOf(op.effect());
        if (existing == null) {
            existing = MenuUserAccess.builder().menuItem(item).user(user).assignedBy(ws.actorId).build();
            ws.userAccess.add(existing);
            ws.created.add(existing);
        }
        existing.setEffect(effect);
        existing.setReason(op.reason());
        existing.setValidTo(op.validTo());
        existing.setAssignedBy(ws.actorId);
    }

    private void setOverride(Workspace ws, int i, MenuOp op) {
        MenuItem item = requireLive(ws, i, op.code());
        if (owned(ws, item)) throw new MenuDraftException(i, "'" + op.code() + "' is your own item; edit it directly");

        MenuTenantOverride o = overrideFor(ws, item);
        o.setLabel(op.label());
        o.setIcon(op.icon());
        o.setSortOrder(op.sortOrder());
        o.setIsHidden(op.hidden() != null && op.hidden());
        tidyOverride(ws, o);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void place(Workspace ws, int i, MenuItem item, MenuItem parent) {
        if (parent != null && !owned(ws, parent) && !parent.getIsActive()) {
            throw new MenuDraftException(i, "Parent '" + parent.getCode() + "' is disabled");
        }
        hierarchy.place(item, parent, ws.live());
    }

    private MenuTenantOverride overrideFor(Workspace ws, MenuItem item) {
        return ws.overrides.stream().filter(o -> sameItem(o.getMenuItem(), item)).findFirst().orElseGet(() -> {
            MenuTenantOverride o = MenuTenantOverride.builder()
                    .tenant(ws.scope.tenant()).menuItem(item).createdBy(ws.actorId).build();
            ws.overrides.add(o);
            ws.created.add(o);
            return o;
        });
    }

    /** An override that inherits everything and hides nothing carries no information. */
    private void tidyOverride(Workspace ws, MenuTenantOverride o) {
        o.setUpdatedBy(ws.actorId);
        boolean empty = o.getLabel() == null && o.getIcon() == null && o.getSortOrder() == null
                && !Boolean.TRUE.equals(o.getIsHidden());
        if (!empty) return;
        ws.overrides.remove(o);
        if (!ws.created.remove(o)) ws.removed.add(o);   // never persisted -> just forget it
    }

    private MenuItem requireLive(Workspace ws, int i, String code) {
        return ws.live().stream().filter(m -> m.getCode().equals(code)).findFirst()
                .orElseThrow(() -> new MenuDraftException(i, "No menu item with code '" + code + "'"));
    }

    private MenuItem findAny(Workspace ws, String code) {
        MenuItem deleted = null;
        for (MenuItem m : ws.items) {
            if (!m.getCode().equals(code)) continue;
            if (m.getDeletedAt() == null) return m;
            deleted = m;
        }
        return deleted;
    }

    private MenuItem parentOrNull(Workspace ws, int i, String parentCode) {
        return parentCode == null ? null : requireLive(ws, i, parentCode);
    }

    private MenuItem requireOwned(Workspace ws, int i, MenuItem item) {
        if (!owned(ws, item)) {
            throw new MenuDraftException(i, "'" + item.getCode() + "' is a platform item and cannot be edited here"
                    + (ws.scope.isGlobal() ? "" : "; use SET_OVERRIDE, SET_ENABLED or SET_ROLES instead"));
        }
        return item;
    }

    private static boolean owned(Workspace ws, MenuItem item) {
        UUID owner = item.getTenant() == null ? null : item.getTenant().getId();
        return Objects.equals(owner, ws.scope.tenantId());
    }

    private static Object keyOf(MenuItem m) {
        return m.getId() != null ? m.getId() : m;
    }

    private static boolean sameItem(MenuItem a, MenuItem b) {
        return keyOf(a).equals(keyOf(b));
    }

    private static List<MenuItem> subtree(Workspace ws, MenuItem root) {
        List<MenuItem> out = new ArrayList<>();
        Deque<MenuItem> pending = new ArrayDeque<>(List.of(root));
        Set<Object> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            MenuItem cur = pending.poll();
            if (!seen.add(keyOf(cur))) continue;
            out.add(cur);
            for (MenuItem m : ws.live()) {
                if (m.getParent() != null && keyOf(m.getParent()).equals(keyOf(cur))) pending.add(m);
            }
        }
        return out;
    }

    private Privilege privilegeOrNull(int i, String code) {
        if (code == null || code.isBlank()) return null;
        return refs.privilege(code).orElseThrow(() -> new MenuDraftException(i, "Unknown privilege '" + code + "'"));
    }

    private String featureOrNull(int i, String code) {
        if (code == null || code.isBlank()) return null;
        if (!refs.featureExists(code)) throw new MenuDraftException(i, "Unknown feature '" + code + "'");
        return code;
    }
}
