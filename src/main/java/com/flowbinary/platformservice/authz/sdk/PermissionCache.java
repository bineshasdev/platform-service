package com.flowbinary.platformservice.authz.sdk;

import com.flowbinary.platformservice.authz.service.PermissionSet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Permission sets per (tenant, user), held for at most {@code app.authz.cache-ttl}
 * (60 s by default) and dropped earlier by change events (design §11). A null
 * tenant is the platform scope.
 */
@Component
public class PermissionCache {

    private record Key(UUID tenantId, UUID userSub) {
    }

    private final Cache<Key, PermissionSet> sets;

    public PermissionCache(@Value("${app.authz.cache-ttl:60s}") Duration ttl,
                           @Value("${app.authz.cache-max-entries:50000}") long maxEntries) {
        this.sets = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .build();
    }

    public PermissionSet get(UUID tenantId, UUID userSub, Supplier<PermissionSet> load) {
        return sets.get(new Key(tenantId, userSub), k -> load.get());
    }

    /** One user in one tenant. */
    public void invalidate(UUID tenantId, UUID userSub) {
        sets.invalidate(new Key(tenantId, userSub));
    }

    /** Every user of one tenant; null tenant = every platform membership. */
    public void invalidateTenant(UUID tenantId) {
        sets.asMap().keySet().removeIf(k -> Objects.equals(k.tenantId(), tenantId));
    }

    public void invalidateAll() {
        sets.invalidateAll();
    }
}
