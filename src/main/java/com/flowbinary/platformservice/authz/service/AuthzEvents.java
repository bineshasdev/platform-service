package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.sdk.PermissionCache;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.event.AuthzChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Announces "who may do what changed" once the change has committed: this
 * instance's cache is cleared at once and other instances (and other services)
 * hear it on {@code platform.authz.events}. Best effort: if the event is lost,
 * cached sets still expire within the cache TTL.
 */
@Slf4j
@Component
public class AuthzEvents {

    private final PermissionCache cache;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafka;
    private final AppProperties properties;

    public AuthzEvents(PermissionCache cache,
                       @Qualifier("kafkaTemplate") ObjectProvider<KafkaTemplate<String, Object>> kafka,
                       AppProperties properties) {
        this.cache = cache;
        this.kafka = kafka;
        this.properties = properties;
    }

    /** One user's access in one tenant changed. */
    public void memberChanged(UUID tenantId, UUID userSub, String reason) {
        afterCommit(() -> {
            cache.invalidate(tenantId, userSub);
            send(AuthzChangedEvent.builder().tenantId(tenantId).userSub(userSub)
                    .platform(tenantId == null).reason(reason).build());
        });
    }

    /** Access of possibly every member of a tenant changed (a role was edited). */
    public void tenantChanged(UUID tenantId, String reason) {
        afterCommit(() -> {
            cache.invalidateTenant(tenantId);
            send(AuthzChangedEvent.builder().tenantId(tenantId).platform(tenantId == null).reason(reason).build());
        });
    }

    /** Applies an event heard from any instance, this one included. */
    public void apply(AuthzChangedEvent event) {
        if (event.getUserSub() != null) {
            cache.invalidate(event.getTenantId(), event.getUserSub());
        } else if (event.getTenantId() != null || event.isPlatform()) {
            cache.invalidateTenant(event.getTenantId());
        } else {
            cache.invalidateAll();
        }
    }

    private static void afterCommit(Runnable announce) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    announce.run();
                }
            });
        } else {
            announce.run();
        }
    }

    private void send(AuthzChangedEvent event) {
        KafkaTemplate<String, Object> template = kafka.getIfAvailable();
        if (template == null) return;
        String key = event.getTenantId() == null ? "platform" : event.getTenantId().toString();
        try {
            template.send(properties.getKafka().getTopics().getAuthzEvents(), key, event)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.warn("Could not publish authz event: {}", ex.getMessage());
                    });
        } catch (RuntimeException e) {
            log.warn("Could not publish authz event: {}", e.getMessage());
        }
    }
}
