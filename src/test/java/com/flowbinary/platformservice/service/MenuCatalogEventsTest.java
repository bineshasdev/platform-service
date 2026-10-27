package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.event.MenuCatalogChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class MenuCatalogEventsTest {

    private MenuCatalogueCache cache;
    private KafkaTemplate<String, Object> template;
    private ObjectProvider<KafkaTemplate<String, Object>> provider;
    private MenuCatalogEvents events;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache = mock(MenuCatalogueCache.class);
        template = mock(KafkaTemplate.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        when(template.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        events = new MenuCatalogEvents(cache, provider, new AppProperties());
    }

    @AfterEach
    void clean() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void outsideATransactionItAnnouncesAtOnce() {
        events.changed("PROVIDER_CONSOLE", tenant);

        verify(cache).invalidateVersions();
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(template).send(eq("platform.menu.catalog.events"), eq("PROVIDER_CONSOLE"), sent.capture());
        MenuCatalogChangedEvent e = (MenuCatalogChangedEvent) sent.getValue();
        assertThat(e.getApplicationCode()).isEqualTo("PROVIDER_CONSOLE");
        assertThat(e.getTenantId()).isEqualTo(tenant);
        assertThat(e.getEventType()).isEqualTo("MENU_CATALOG_CHANGED");
    }

    @Test
    void insideATransactionNothingHappensUntilItCommits() {
        TransactionSynchronizationManager.initSynchronization();

        events.changed("PROVIDER_CONSOLE", null);
        verifyNoInteractions(cache, template);                       // not before commit

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).invalidateVersions();
        verify(template).send(anyString(), anyString(), any());
    }

    @Test
    void aRolledBackChangeAnnouncesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        events.changed("PROVIDER_CONSOLE", tenant);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(cache, template);
    }

    @Test
    void kafkaFailureDoesNotBreakTheChangeAndStillRefreshesThisInstance() {
        when(template.send(anyString(), anyString(), any())).thenThrow(new IllegalStateException("broker down"));

        events.changed("PROVIDER_CONSOLE", tenant);                 // must not throw

        verify(cache).invalidateVersions();
    }

    @Test
    void asyncSendFailureIsSwallowed() {
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        when(template.send(anyString(), anyString(), any())).thenReturn((CompletableFuture) failed);

        events.changed("PROVIDER_CONSOLE", tenant);

        verify(cache).invalidateVersions();
    }

    @Test
    void worksWithoutKafkaConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);

        events.changed("PROVIDER_CONSOLE", tenant);

        verify(cache).invalidateVersions();
        verifyNoInteractions(template);
    }
}
