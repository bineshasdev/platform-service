package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.service.MenuCatalogueCompiler.Versions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-instance cache of compiled catalogues.
 *
 * <p><b>Keying.</b> Entries are filed under {@code (application, tenant, globalVersion,
 * tenantVersion)}. A change moves a version counter (a database trigger does this on
 * any write), the next lookup misses, and the old entry just ages out — nothing is
 * ever invalidated by hand, so there is no invalidation to get wrong.
 *
 * <p><b>Staleness.</b> The counters themselves are re-read at most every
 * {@code versionCheck} (default 2 s), which is therefore the worst-case staleness
 * across instances. {@link #invalidateVersions()} (called right after a local commit
 * and on a Kafka event) makes the next lookup re-read them at once. {@code catalogueTtl}
 * bounds the damage of anything that bypasses the triggers.
 *
 * <p><b>Stampede.</b> Concurrent misses for one key compile once; the rest wait.
 */
@Slf4j
@Component
public class MenuCatalogueCache {

    private record Key(String application, UUID tenant, long globalVersion, long tenantVersion) {
    }

    private record VersionKey(String application, UUID tenant) {
    }

    private final MenuCatalogueCompiler compiler;
    private final MenuMetrics metrics;
    private final Cache<Key, MenuCatalogue> catalogues;
    private final Cache<VersionKey, Versions> versions;

    public MenuCatalogueCache(MenuCatalogueCompiler compiler, MenuMetrics metrics, AppProperties properties) {
        this.compiler = compiler;
        this.metrics = metrics;
        AppProperties.MenuProperties.CacheProperties c = properties.getMenu().getCache();
        this.catalogues = Caffeine.newBuilder()
                .maximumSize(c.getMaxEntries())
                .expireAfterWrite(c.getCatalogueTtl())
                .recordStats()
                .build();
        this.versions = Caffeine.newBuilder()
                .maximumSize(c.getMaxEntries())
                .expireAfterWrite(c.getVersionCheck())
                .build();
    }

    @PostConstruct
    void bindMetrics() {
        CaffeineCacheMetrics.monitor(metrics.registry(), catalogues, "menu.catalogue");
    }

    /** The catalogue for a tenant at the latest version this instance knows about. */
    public MenuCatalogue get(String applicationCode, UUID tenantId) {
        Versions v = currentVersions(applicationCode, tenantId);
        return catalogues.get(new Key(applicationCode, tenantId, v.global(), v.tenant()), k -> {
            log.debug("Menu catalogue miss {} tenant={} v={}/{}", applicationCode, tenantId, v.global(), v.tenant());
            return metrics.observe("menu.catalogue.compile", () -> compiler.compile(applicationCode, tenantId, v),
                    "application", applicationCode);
        });
    }

    /** Reads straight from the database and stores nothing. For previews, which see uncommitted drafts. */
    public MenuCatalogue compileFresh(String applicationCode, UUID tenantId) {
        Versions v = compiler.versions(applicationCode, tenantId);
        return compiler.compile(applicationCode, tenantId, v);
    }

    /** Next lookup re-reads the version counters instead of trusting the last read. */
    public void invalidateVersions() {
        versions.invalidateAll();
        metrics.count("menu.catalogue.invalidate");
    }

    /** Test/ops hook: drop everything. */
    public void clear() {
        versions.invalidateAll();
        catalogues.invalidateAll();
    }

    private Versions currentVersions(String applicationCode, UUID tenantId) {
        return versions.get(new VersionKey(applicationCode, tenantId), k -> {
            Versions v = compiler.versions(applicationCode, tenantId);
            return v;
        });
    }

    /** Visible for tests. */
    Duration versionCheckInterval(AppProperties properties) {
        return properties.getMenu().getCache().getVersionCheck();
    }
}
