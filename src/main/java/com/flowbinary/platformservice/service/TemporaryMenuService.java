package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuRequest.Audience;
import com.flowbinary.platformservice.dto.menu.TemporaryMenuResponse;
import com.flowbinary.platformservice.exception.MenuConflictException;
import com.flowbinary.platformservice.exception.MenuDraftException;
import com.flowbinary.platformservice.exception.MenuHierarchyException;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.MenuItemRepository;
import com.flowbinary.platformservice.repository.MenuUserAccessRepository;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Time-bound menus for feature previews, announcements, campaigns and pilots.
 *
 * <p>Unlike permanent menus these are <b>live</b>, not drafted: a create,
 * update or cancel takes effect immediately and is audited — the start time is
 * the delay. They are invisible to drafts, snapshots and rollback, so rolling
 * a menu back never deletes a running campaign.
 *
 * <ul>
 *   <li>Times are UTC instants; the end is exclusive. Visibility is decided at
 *       request time from the window, so a late scheduler cannot leave an
 *       expired menu on screen.</li>
 *   <li>A temporary menu may sit at the root or under any visible parent, but is
 *       always a leaf (see {@link MenuHierarchyValidator}).</li>
 *   <li>Overlapping menus all show; there is no priority-wins rule. {@code priority}
 *       only orders siblings.</li>
 *   <li>Expired and cancelled menus stay in the table; an expired one can be
 *       revived by updating its window.</li>
 *   <li>The audience reuses role, user and group grants, so the usual rule holds:
 *       a grant never bypasses the plan, feature or privilege gates.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TemporaryMenuService {

    private final MenuScopeLoader           loader;
    private final MenuHierarchyValidator    hierarchy;
    private final MenuReferences            refs;
    private final MenuItemRepository        items;
    private final MenuUserAccessRepository  userAccess;
    private final AuditService              audit;
    private final MenuCatalogEvents         events;

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TemporaryMenuResponse> list(AdminContext ctx, MenuLifecycle status) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        List<MenuItem> mine = ws.items.stream()
                .filter(m -> m.isTemporary() && owned(ctx, m) && (status == null || m.getLifecycleStatus() == status))
                .sorted(Comparator.comparing(MenuItem::getStartsAt).reversed())
                .toList();
        return toResponses(mine);
    }

    @Transactional(readOnly = true)
    public TemporaryMenuResponse get(AdminContext ctx, String code) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        return toResponses(List.of(requireOwned(ctx, ws, code))).get(0);
    }

    // ── Commands ───────────────────────────────────────────────────────────

    @Transactional
    public TemporaryMenuResponse create(AdminContext ctx, TemporaryMenuRequest req) {
        authorise(ctx, req);
        Instant now = Instant.now();
        Instant start = req.startsAt() == null ? now : req.startsAt();
        validateContent(req, req.code(), ctx);
        validateWindow(start, req.endsAt(), now);

        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        if (ws.items.stream().anyMatch(m -> m.getCode().equals(req.code()))) {
            throw new MenuConflictException("Code '" + req.code() + "' is already used"
                    + " (expired and cancelled menus keep their code; update it to run it again)");
        }
        if (ctx.scope().isGlobal() && refs.codeUsedByAnyTenant(ctx.scope().application().getId(), req.code())) {
            throw new MenuConflictException("Code '" + req.code() + "' is already used by a tenant");
        }
        refs.moduleNamespaceOf(req.code()).ifPresent(m -> {
            throw new MenuConflictException("Code '" + req.code() + "' is in the namespace of module '" + m + "'");
        });

        MenuItem item = MenuItem.builder()
                .application(ctx.scope().application())
                .tenant(ctx.scope().tenant())
                .source(MenuSource.TEMPORARY)
                .code(req.code())
                .createdBy(ctx.actorId())
                .build();
        fill(ctx, item, req, start, now);
        place(ctx, ws, item, req.parentCode());

        items.saveAndFlush(item);
        setAudience(ctx, item, req.audience(), true);

        record(ctx, "MENU_TEMP_CREATED", item, null);
        events.changed(ctx.scope().application().getCode(), ctx.scope().tenantId());
        return toResponses(List.of(item)).get(0);
    }

    @Transactional
    public TemporaryMenuResponse update(AdminContext ctx, String code, TemporaryMenuRequest req) {
        authorise(ctx, req);
        Instant now = Instant.now();
        Instant start = req.startsAt() == null ? now : req.startsAt();
        validateContent(req, code, ctx);
        validateWindow(start, req.endsAt(), now);

        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        MenuItem item = requireOwned(ctx, ws, code);
        if (item.getLifecycleStatus() == MenuLifecycle.CANCELLED) {
            throw new MenuConflictException("'" + code + "' was cancelled; create a new temporary menu instead");
        }
        Map<String, Object> before = summary(item);

        fill(ctx, item, req, start, now);       // re-derives the status, so an EXPIRED menu can be revived
        place(ctx, ws, item, req.parentCode());
        setAudience(ctx, item, req.audience(), false);

        items.saveAndFlush(item);
        record(ctx, "MENU_TEMP_UPDATED", item, before);
        events.changed(ctx.scope().application().getCode(), ctx.scope().tenantId());
        return toResponses(List.of(item)).get(0);
    }

    @Transactional
    public TemporaryMenuResponse cancel(AdminContext ctx, String code, String reason) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        MenuItem item = requireOwned(ctx, ws, code);
        if (item.getLifecycleStatus() == MenuLifecycle.CANCELLED) return toResponses(List.of(item)).get(0);

        Map<String, Object> before = summary(item);
        item.setLifecycleStatus(MenuLifecycle.CANCELLED);
        item.setUpdatedBy(ctx.actorId());
        audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), "MENU_TEMP_CANCELLED",
                "MENU_TEMPORARY", code, before, summary(item), reason, "API"));
        events.changed(ctx.scope().application().getCode(), ctx.scope().tenantId());
        return toResponses(List.of(item)).get(0);
    }

    // ── Building the item ──────────────────────────────────────────────────

    private void fill(AdminContext ctx, MenuItem item, TemporaryMenuRequest req, Instant start, Instant now) {
        item.setLabel(req.label());
        item.setDescription(req.description());
        item.setIcon(req.icon());
        item.setRoute(req.route());
        item.setType(req.type() == null ? MenuItemType.LINK : MenuItemType.valueOf(req.type()));
        item.setTarget(req.target() == null ? MenuTarget.SELF : MenuTarget.valueOf(req.target()));
        item.setBehavior(MenuBehavior.NAVIGATE);
        item.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        item.setPriority(req.priority() == null ? 0 : req.priority());
        item.setBadge(req.badge());
        item.setStartsAt(start);
        item.setEndsAt(req.endsAt());
        item.setLifecycleStatus(start.isAfter(now) ? MenuLifecycle.SCHEDULED : MenuLifecycle.ACTIVE);
        item.setIsActive(true);
        item.setIsVisible(true);
        item.setUpdatedBy(ctx.actorId());

        String mode = req.audience() == null || req.audience().mode() == null ? "RESTRICTED" : req.audience().mode();
        item.setAudienceMode(MenuAudienceMode.valueOf(mode));

        item.setRequiredPrivilege(blank(req.requiredPrivilege()) ? null : refs.privilege(req.requiredPrivilege())
                .orElseThrow(() -> new MenuDraftException(-1, "Unknown privilege '" + req.requiredPrivilege() + "'")));
        if (!blank(req.requiredFeature()) && !refs.featureExists(req.requiredFeature())) {
            throw new MenuDraftException(-1, "Unknown feature '" + req.requiredFeature() + "'");
        }
        item.setRequiredFeature(blank(req.requiredFeature()) ? null : req.requiredFeature());

        Set<TenantPlan> wanted = req.plans() == null || req.plans().isEmpty()
                ? EnumSet.allOf(TenantPlan.class)
                : req.plans().stream().map(TenantPlan::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(TenantPlan.class)));
        item.getPlanAccess().removeIf(pa -> !wanted.contains(pa.getId().getPlan()));
        Set<TenantPlan> present = item.getPlanAccess().stream().map(pa -> pa.getId().getPlan()).collect(Collectors.toSet());
        for (TenantPlan p : wanted) {
            if (!present.contains(p)) {
                item.getPlanAccess().add(MenuPlanAccess.builder()
                        .id(new MenuPlanAccessId(item.getId(), p)).menuItem(item).build());
            }
        }
    }

    private void place(AdminContext ctx, MenuChangeApplier.Workspace ws, MenuItem item, String parentCode) {
        MenuItem parent = null;
        if (parentCode != null) {
            parent = ws.live().stream().filter(m -> m.getCode().equals(parentCode)).findFirst()
                    .orElseThrow(() -> new MenuDraftException(-1, "No menu item with code '" + parentCode + "'"));
        }
        if (!ws.items.contains(item)) ws.items.add(item);
        // A campaign item renders where its parent does: under a settings page it is a settings entry.
        item.setPlacement(parent == null ? MenuPlacement.LEFT : parent.getPlacement());
        try {
            hierarchy.place(item, parent, ws.live());
        } catch (MenuHierarchyException e) {
            throw new MenuDraftException(-1, e.getMessage());
        }
    }

    /**
     * Replaces the audience: the named users (RESTRICTED), through GRANT rows that lapse with the menu.
     * Reaching a kind of person is done with the menu's required privilege, not with roles.
     */
    private void setAudience(AdminContext ctx, MenuItem item, Audience a, boolean isNew) {
        Set<UUID> userIds = a == null || a.users() == null ? Set.of() : a.users();
        MenuScope scope = ctx.scope();

        if (scope.isGlobal() && !userIds.isEmpty()) {
            throw new MenuDraftException(-1, "Users belong to a tenant; a platform-wide menu can target everyone eligible "
                    + "(narrowed by its required privilege), and a tenant can also name its own users");
        }

        Map<UUID, MenuUserAccess> haveUsers = isNew ? new HashMap<>() : userAccess.findByMenuItemIdIn(List.of(item.getId()))
                .stream().collect(Collectors.toMap(x -> x.getUser().getId(), x -> x));
        haveUsers.values().stream().filter(x -> !userIds.contains(x.getUser().getId())).forEach(userAccess::delete);
        for (UUID id : userIds) {
            TenantUser u = refs.tenantUser(scope.tenantId(), id)
                    .orElseThrow(() -> new MenuDraftException(-1, "User not found in this tenant"));
            MenuUserAccess row = haveUsers.getOrDefault(id,
                    MenuUserAccess.builder().menuItem(item).user(u).assignedBy(ctx.actorId()).build());
            row.setEffect(MenuAccessEffect.GRANT);
            row.setReason("Temporary menu");
            row.setValidTo(item.getEndsAt());          // the grant lapses with the menu
            userAccess.save(row);
        }
    }

    // ── Validation / helpers ───────────────────────────────────────────────

    private void authorise(AdminContext ctx, TemporaryMenuRequest req) {
        ctx.require(MenuAdminAccess.MANAGE);
        Audience a = req.audience();
        boolean targeted = a != null && a.users() != null && !a.users().isEmpty();
        if (targeted) ctx.require(MenuAdminAccess.ASSIGN);
        if (a != null && a.mode() != null && !a.mode().equals("ALL_ELIGIBLE") && !a.mode().equals("RESTRICTED")) {
            throw new MenuDraftException(-1, "audience.mode must be ALL_ELIGIBLE or RESTRICTED");
        }
    }

    /** Reuses the field rules of draft operations so both paths refuse the same input. */
    private void validateContent(TemporaryMenuRequest req, String code, AdminContext ctx) {
        if (req.type() != null && !req.type().equals("LINK") && !req.type().equals("EXTERNAL")) {
            throw new MenuDraftException(-1, "type must be LINK or EXTERNAL");
        }
        if (req.route() == null) throw new MenuDraftException(-1, "route is required");
        try {
            MenuOpValidator.validate(0, MenuOp.builder().op(MenuOpType.PUT_ITEM).code(code).label(req.label())
                    .description(req.description()).icon(req.icon()).route(req.route()).type(req.type())
                    .target(req.target()).parentCode(req.parentCode()).plans(req.plans()).build(), ctx.scope());
        } catch (MenuDraftException e) {
            throw new MenuDraftException(-1, e.getMessage());
        }
    }

    private static void validateWindow(Instant start, Instant end, Instant now) {
        if (!end.isAfter(now)) throw new MenuDraftException(-1, "endsAt must be in the future; cancel the menu to end it now");
        if (!end.isAfter(start)) throw new MenuDraftException(-1, "endsAt must be after startsAt");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean owned(AdminContext ctx, MenuItem m) {
        UUID owner = m.getTenant() == null ? null : m.getTenant().getId();
        return Objects.equals(owner, ctx.scope().tenantId());
    }

    /** Same answer for "no such code", "someone else's" and "not a temporary menu". */
    private MenuItem requireOwned(AdminContext ctx, MenuChangeApplier.Workspace ws, String code) {
        return ws.items.stream()
                .filter(m -> m.getCode().equals(code) && m.isTemporary() && owned(ctx, m) && m.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Temporary menu not found"));
    }

    private Map<String, Object> summary(MenuItem m) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("label", m.getLabel());
        a.put("route", m.getRoute());
        a.put("parentCode", m.getParent() == null ? null : m.getParent().getCode());
        a.put("status", m.getLifecycleStatus().name());
        a.put("startsAt", m.getStartsAt() == null ? null : m.getStartsAt().toString());
        a.put("endsAt", m.getEndsAt() == null ? null : m.getEndsAt().toString());
        a.put("audienceMode", m.getAudienceMode().name());
        a.put("priority", m.getPriority());
        a.put("badge", m.getBadge());
        return a;
    }

    private void record(AdminContext ctx, String action, MenuItem item, Map<String, Object> before) {
        audit.record(new AuditService.Entry(ctx.scope().tenantId(), ctx.actorId(), action, "MENU_TEMPORARY",
                item.getCode(), before, summary(item), null, "API"));
    }

    private List<TemporaryMenuResponse> toResponses(List<MenuItem> list) {
        List<UUID> ids = list.stream().map(MenuItem::getId).filter(Objects::nonNull).toList();
        Map<UUID, List<MenuUserAccess>> users = ids.isEmpty() ? Map.of() : userAccess.findByMenuItemIdIn(ids).stream()
                .collect(Collectors.groupingBy(x -> x.getMenuItem().getId()));
        Instant now = Instant.now();

        return list.stream().map(m -> new TemporaryMenuResponse(
                m.getCode(), m.getLabel(), m.getDescription(), m.getIcon(), m.getRoute(),
                m.getType().name(), m.getTarget().name(), m.getParent() == null ? null : m.getParent().getCode(),
                m.getSortOrder(), m.getPriority(), m.getBadge(), m.getLifecycleStatus().name(),
                m.getStartsAt(), m.getEndsAt(), m.isLiveAt(now),
                m.getRequiredPrivilege() == null ? null : m.getRequiredPrivilege().getCode(), m.getRequiredFeature(),
                m.getAudienceMode().name(),
                users.getOrDefault(m.getId(), List.of()).stream().map(x -> x.getUser().getId()).sorted().toList(),
                m.getCreatedBy())).toList();
    }
}
