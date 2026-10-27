package com.flowbinary.platformservice.messaging;

import com.flowbinary.platformservice.event.MenuCatalogChangedEvent;
import com.flowbinary.platformservice.service.MenuCatalogueCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Receives "menu catalogue changed" on <em>every</em> instance. The consumer group
 * is unique per instance (random suffix, fixed at start-up) and starts from the
 * latest offset: this is a broadcast, not work to be shared, and a missed or old
 * message is harmless because the periodic version check is the safety net.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuCacheInvalidationListener {

    private final MenuCatalogueCache cache;

    @KafkaListener(
            topics = "${app.kafka.topics.menu-catalog-events}",
            groupId = "${spring.application.name}-menu-cache-${random.uuid}",
            properties = "auto.offset.reset=latest",
            containerFactory = "kafkaListenerContainerFactory")
    public void onCatalogChanged(@Payload MenuCatalogChangedEvent event, Acknowledgment ack) {
        log.debug("Menu catalogue changed: app={} tenant={}", event.getApplicationCode(), event.getTenantId());
        cache.invalidateVersions();
        ack.acknowledge();
    }
}
