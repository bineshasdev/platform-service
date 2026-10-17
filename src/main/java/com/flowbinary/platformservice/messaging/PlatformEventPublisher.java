package com.flowbinary.platformservice.messaging;

import com.flowbinary.platform.event.avro.NotificationEvent;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.event.ClinicCreatedEvent;
import com.flowbinary.platformservice.event.DoctorRegisteredEvent;
import com.flowbinary.platformservice.event.FeatureToggledEvent;
import com.flowbinary.platformservice.event.MenuItemUpdatedEvent;
import com.flowbinary.platformservice.event.PatientRegisteredEvent;
import com.flowbinary.platformservice.event.PlatformEvent;
import com.flowbinary.platformservice.event.TenantCreatedEvent;
import com.flowbinary.platformservice.event.TenantPlanChangedEvent;
import com.flowbinary.platformservice.event.TenantVerificationChangedEvent;
import com.flowbinary.platformservice.event.UserCreatedEvent;
import com.flowbinary.platformservice.event.UserDeletedEvent;
import com.flowbinary.platformservice.event.UserUpdatedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes all {@link PlatformEvent} subtypes as Avro {@link NotificationEvent} records
 * to the single {@code platform.notification.events} topic.
 */
@Slf4j
@Component
public class PlatformEventPublisher {

    private final KafkaTemplate<String, SpecificRecord> avroKafkaTemplate;
    private final AppProperties appProperties;

    public PlatformEventPublisher(
            @Qualifier("avroKafkaTemplate") KafkaTemplate<String, SpecificRecord> avroKafkaTemplate,
            AppProperties appProperties) {
        this.avroKafkaTemplate = avroKafkaTemplate;
        this.appProperties     = appProperties;
    }

    // ── Tenant events ──────────────────────────────────────────────────────

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishTenantCreated(TenantCreatedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishClinicCreated(ClinicCreatedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishTenantPlanChanged(TenantPlanChangedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishTenantVerificationChanged(TenantVerificationChangedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    // ── Practitioner / patient events ──────────────────────────────────────

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishDoctorRegistered(DoctorRegisteredEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishPatientRegistered(PatientRegisteredEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    // ── Feature event ──────────────────────────────────────────────────────

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishFeatureToggled(FeatureToggledEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    // ── Menu event ─────────────────────────────────────────────────────────

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishMenuItemUpdated(MenuItemUpdatedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    // ── User events ────────────────────────────────────────────────────────

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishUserCreated(UserCreatedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishUserUpdated(UserUpdatedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    @CircuitBreaker(name = "kafka-publisher")
    @Retry(name = "kafka-publisher")
    public void publishUserDeleted(UserDeletedEvent event) {
        publish(NotificationEventMapper.toNotificationEvent(event));
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * A failed notification must never fail the caller's business transaction (e.g. a
     * clinic registration) — so every exception here is logged and swallowed, never
     * propagated. This used to be delegated to a {@code fallbackMethod} on
     * {@code @CircuitBreaker}, but resilience4j's fallback-method resolution requires an
     * exact parameter-type match against each {@code publishX}'s own event type (verified
     * against the library's bytecode); a fallback typed to the {@link PlatformEvent}
     * supertype never matches any of them, so it silently never ran and the original
     * exception propagated anyway. Catching here is what actually provides the guarantee.
     */
    private void publish(NotificationEvent event) {
        String topic = appProperties.getKafka().getTopics().getNotificationEvents();
        String key   = event.getAudience().getTenantId();

        try {
            CompletableFuture<SendResult<String, SpecificRecord>> future =
                    avroKafkaTemplate.send(topic, key, event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish NotificationEvent [{}] to [{}]: {}",
                            event.getEventType(), topic, ex.getMessage(), ex);
                } else {
                    log.debug("Published NotificationEvent [{}] → topic [{}] partition [{}] offset [{}]",
                            event.getEventType(), topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            // KafkaProducer#send serializes synchronously, so a schema-registry outage or
            // an unencodable payload throws here directly — before any future even exists —
            // which the async whenComplete() above never sees.
            log.error("Failed to publish NotificationEvent [{}] to [{}]: {}",
                    event.getEventType(), topic, ex.getMessage(), ex);
            // TODO: persist to outbox table for guaranteed delivery
        }
    }
}
