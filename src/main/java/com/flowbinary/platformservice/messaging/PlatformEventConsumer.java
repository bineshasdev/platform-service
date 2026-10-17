package com.flowbinary.platformservice.messaging;

import com.flowbinary.platformservice.event.FeatureToggledEvent;
import com.flowbinary.platformservice.event.MenuItemUpdatedEvent;
import com.flowbinary.platformservice.event.TenantCreatedEvent;
import com.flowbinary.platformservice.event.TenantPlanChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlatformEventConsumer {

    @KafkaListener(
            topics = "${app.kafka.topics.tenant-events}",
            groupId = "${spring.application.name}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTenantCreated(
            @Payload TenantCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            log.info("Received TenantCreatedEvent: tenantId={} slug={} topic={} offset={}",
                    event.getTenantId(), event.getSlug(), topic, offset);
            // Handle: seed default settings, create default theme, etc.
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing TenantCreatedEvent eventId={}: {}",
                    event.getEventId(), ex.getMessage(), ex);
            throw ex; // triggers retry / DLQ
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.tenant-events}",
            groupId = "${spring.application.name}-plan",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTenantPlanChanged(
            @Payload TenantPlanChangedEvent event,
            Acknowledgment ack) {
        try {
            log.info("Received TenantPlanChangedEvent: tenantId={} {} -> {}",
                    event.getTenantId(), event.getPreviousPlan(), event.getNewPlan());
            // Handle: apply new plan features, revoke old plan features
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing TenantPlanChangedEvent: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.feature-events}",
            groupId = "${spring.application.name}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onFeatureToggled(
            @Payload FeatureToggledEvent event,
            Acknowledgment ack) {
        try {
            log.info("Received FeatureToggledEvent: tenantId={} feature={} enabled={}",
                    event.getTenantId(), event.getFeatureCode(), event.isEnabled());
            // Handle: sync menu visibility, invalidate cache
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing FeatureToggledEvent: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.menu-events}",
            groupId = "${spring.application.name}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMenuItemUpdated(
            @Payload MenuItemUpdatedEvent event,
            Acknowledgment ack) {
        try {
            log.info("Received MenuItemUpdatedEvent: menuItemId={} code={} action={}",
                    event.getMenuItemId(), event.getCode(), event.getAction());
            // Handle: invalidate menu cache across services
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing MenuItemUpdatedEvent: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Dead-letter consumer — logs and alerts, does not re-throw.
     */
    @KafkaListener(
            topicPattern = "platform\\..*\\.DLT",
            groupId = "${spring.application.name}-dlt",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDeadLetter(
            @Payload String rawPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {
        log.error("Dead-letter message on topic [{}]: {}", topic, rawPayload);
        // TODO: persist to dead_letter_events table, trigger alert
        ack.acknowledge();
    }
}
