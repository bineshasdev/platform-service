package com.flowbinary.platformservice.messaging;

import com.flowbinary.platformservice.event.MenuCatalogChangedEvent;
import com.flowbinary.platformservice.service.MenuCatalogueCache;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MenuCacheInvalidationListenerTest {

    @Test
    void anEventClearsTheVersionCacheAndIsAcknowledged() {
        MenuCatalogueCache cache = mock(MenuCatalogueCache.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        MenuCatalogChangedEvent event = MenuCatalogChangedEvent.builder()
                .applicationCode("PROVIDER_CONSOLE").tenantId(UUID.randomUUID()).build();

        new MenuCacheInvalidationListener(cache).onCatalogChanged(event, ack);

        verify(cache).invalidateVersions();
        verify(ack).acknowledge();
    }

    @Test
    void everyInstanceListensOnItsOwnGroupFromTheLatestOffset() throws Exception {
        KafkaListener l = MenuCacheInvalidationListener.class
                .getMethod("onCatalogChanged", MenuCatalogChangedEvent.class, Acknowledgment.class)
                .getAnnotation(KafkaListener.class);

        // a shared group would deliver each message to only one instance; this is a broadcast
        assertThat(l.groupId()).contains("${random.uuid}");
        assertThat(l.properties()).contains("auto.offset.reset=latest");
        assertThat(l.topics()).containsExactly("${app.kafka.topics.menu-catalog-events}");
    }
}
