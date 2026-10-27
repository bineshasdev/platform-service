package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.event.MenuCatalogChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Announces "this menu changed" once the change has committed: it clears this
 * instance's version cache immediately (so the editor sees their own change
 * without waiting) and tells the other instances over Kafka. Best effort by design —
 * if the event is lost, the periodic version check catches up within seconds.
 */
@Slf4j
@Component
public class MenuCatalogEvents {

    private final MenuCatalogueCache cache;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafka;
    private final AppProperties properties;

    public MenuCatalogEvents(MenuCatalogueCache cache,
                             @Qualifier("kafkaTemplate") ObjectProvider<KafkaTemplate<String, Object>> kafka,
                             AppProperties properties) {
        this.cache = cache;
        this.kafka = kafka;
        this.properties = properties;
    }

    /** @param tenantId null when the platform-wide menu changed */
    public void changed(String applicationCode, UUID tenantId) {
        Runnable announce = () -> {
            cache.invalidateVersions();
            send(applicationCode, tenantId);
        };
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

    private void send(String applicationCode, UUID tenantId) {
        KafkaTemplate<String, Object> template = kafka.getIfAvailable();
        if (template == null) return;
        try {
            MenuCatalogChangedEvent event = MenuCatalogChangedEvent.builder()
                    .applicationCode(applicationCode).tenantId(tenantId).build();
            template.send(properties.getKafka().getTopics().getMenuCatalogEvents(), applicationCode, event)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.warn("Could not publish menu catalogue event: {}", ex.getMessage());
                    });
        } catch (RuntimeException e) {
            log.warn("Could not publish menu catalogue event: {}", e.getMessage());
        }
    }
}
