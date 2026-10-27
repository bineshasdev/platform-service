package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.MenuGroupAccess;
import com.flowbinary.platformservice.model.MenuItem;
import com.flowbinary.platformservice.model.MenuAccessEffect;
import com.flowbinary.platformservice.repository.MenuGroupAccessRepository;
import com.flowbinary.platformservice.repository.MenuItemRepository;
import com.flowbinary.platformservice.repository.MenuTenantOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Builds a {@link MenuCatalogue} from the database, and reads the version counters that key the cache. */
@Component
@RequiredArgsConstructor
public class MenuCatalogueCompiler {

    /** Counters of the platform-wide scope and of one tenant's scope. */
    public record Versions(long global, long tenant) {
    }

    private final MenuItemRepository           items;
    private final MenuTenantOverrideRepository overrides;
    private final MenuGroupAccessRepository    groupAccess;
    private final JdbcTemplate                 jdbc;

    @Transactional(readOnly = true)
    public Versions versions(String applicationCode, UUID tenantId) {
        long global = 0, tenant = 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT v.tenant_id IS NULL AS is_global, v.version
                FROM menu_catalog_version v JOIN applications a ON a.id = v.application_id
                WHERE a.code = ? AND (v.tenant_id IS NULL OR v.tenant_id = ?)
                """, applicationCode, tenantId);
        for (Map<String, Object> r : rows) {
            long version = ((Number) r.get("version")).longValue();
            if (Boolean.TRUE.equals(r.get("is_global"))) global = version; else tenant = version;
        }
        return new Versions(global, tenant);
    }

    /**
     * Compiles the catalogue. Callers read {@code versions} first and pass them in,
     * so the data is never older than the version it is filed under.
     */
    @Transactional(readOnly = true)
    public MenuCatalogue compile(String applicationCode, UUID tenantId, Versions v) {
        Instant now = Instant.now();
        List<CatalogItem> compiled = items.findCatalogueSource(applicationCode, tenantId, now).stream()
                .map(CatalogItem::of)
                .toList();

        Map<UUID, CatalogOverride> tenantOverrides = new HashMap<>();
        overrides.findByTenantId(tenantId).forEach(o -> tenantOverrides.put(o.getMenuItem().getId(), CatalogOverride.of(o)));

        Map<UUID, Set<UUID>> denied = new HashMap<>(), granted = new HashMap<>();
        for (MenuGroupAccess a : groupAccess.findForTenantAndApplicationCode(tenantId, applicationCode)) {
            Map<UUID, Set<UUID>> target = a.getEffect() == MenuAccessEffect.DENY ? denied : granted;
            target.computeIfAbsent(a.getGroup().getId(), k -> new HashSet<>()).add(a.getMenuItem().getId());
        }
        return new MenuCatalogue(compiled, Map.copyOf(tenantOverrides), freeze(denied), freeze(granted),
                v.global(), v.tenant(), now);
    }

    private static Map<UUID, Set<UUID>> freeze(Map<UUID, Set<UUID>> m) {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, Set.copyOf(v)));
        return Map.copyOf(out);
    }
}
