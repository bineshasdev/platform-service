package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.menu.CatalogueItem;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** The published catalogue of a scope, for the admin editor — disabled and hidden items included. */
@Service
@RequiredArgsConstructor
public class MenuCatalogueService {

    private final MenuScopeLoader loader;

    @Transactional(readOnly = true)
    public List<CatalogueItem> catalogue(AdminContext ctx) {
        ctx.require(MenuAdminAccess.MANAGE);
        MenuChangeApplier.Workspace ws = loader.load(ctx.scope(), ctx.actorId());
        UUID tenantId = ctx.scope().tenantId();

        Map<UUID, MenuTenantOverride> overrides = new HashMap<>();
        ws.overrides.forEach(o -> overrides.put(o.getMenuItem().getId(), o));

        Map<UUID, List<MenuGroupAccess>> byItem = new HashMap<>();
        ws.groupAccess.forEach(a -> byItem.computeIfAbsent(a.getMenuItem().getId(), k -> new ArrayList<>()).add(a));

        return ws.live().stream()
                .sorted(Comparator.comparingInt(MenuItem::getDepth).thenComparingInt(MenuItem::getSortOrder))
                .map(m -> {
                    UUID owner = m.getTenant() == null ? null : m.getTenant().getId();
                    boolean editable = Objects.equals(owner, tenantId) && !m.isTemporary() && !m.isModule();
                    MenuTenantOverride o = overrides.get(m.getId());
                    return new CatalogueItem(m.getCode(), m.getParent() == null ? null : m.getParent().getCode(),
                            m.getLabel(), m.getIcon(), m.getRoute(), m.getType().name(), m.getBehavior().name(),
                            m.getTarget().name(), m.getPlacement().name(), m.getSortOrder(), m.getDepth(),
                            m.getIsActive(), m.getIsVisible(), editable,
                            m.getRequiredPrivilege() == null ? null : m.getRequiredPrivilege().getCode(),
                            m.getRequiredFeature(),
                            m.getRoleAccess().stream().map(r -> r.getRole().getCode()).sorted().toList(),
                            m.getPlanAccess().stream().map(p -> p.getId().getPlan().name()).sorted().toList(),
                            m.getTenantTypes() == null ? null : m.getTenantTypes().stream().sorted().toList(),
                            o == null ? null : new CatalogueItem.Override(o.getLabel(), o.getIcon(), o.getSortOrder(),
                                    Boolean.TRUE.equals(o.getIsHidden())),
                            groupCodes(byItem.get(m.getId()), MenuAccessEffect.GRANT),
                            groupCodes(byItem.get(m.getId()), MenuAccessEffect.DENY),
                            m.getSource().name(), m.getLifecycleStatus().name(), m.getStartsAt(), m.getEndsAt(),
                            m.getModule() == null ? null : m.getModule().getCode());
                })
                .toList();
    }

    private static List<String> groupCodes(List<MenuGroupAccess> rows, MenuAccessEffect effect) {
        if (rows == null) return List.of();
        return rows.stream().filter(a -> a.getEffect() == effect).map(a -> a.getGroup().getCode()).sorted().toList();
    }
}
