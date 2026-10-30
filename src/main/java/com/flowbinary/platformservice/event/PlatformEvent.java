package com.flowbinary.platformservice.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all platform domain events published to Kafka.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TenantCreatedEvent.class,    name = "TENANT_CREATED"),
        @JsonSubTypes.Type(value = ClinicCreatedEvent.class,    name = "CLINIC_CREATED"),
        @JsonSubTypes.Type(value = TenantPlanChangedEvent.class, name = "TENANT_PLAN_CHANGED"),
        @JsonSubTypes.Type(value = TenantVerificationChangedEvent.class, name = "TENANT_VERIFICATION_CHANGED"),
        @JsonSubTypes.Type(value = DoctorRegisteredEvent.class,  name = "DOCTOR_REGISTERED"),
        @JsonSubTypes.Type(value = PatientRegisteredEvent.class, name = "PATIENT_REGISTERED"),
        @JsonSubTypes.Type(value = FeatureToggledEvent.class,   name = "FEATURE_TOGGLED"),
        @JsonSubTypes.Type(value = MenuItemUpdatedEvent.class,  name = "MENU_ITEM_UPDATED"),
        @JsonSubTypes.Type(value = MenuCatalogChangedEvent.class, name = "MENU_CATALOG_CHANGED"),
        @JsonSubTypes.Type(value = AuthzChangedEvent.class,     name = "AUTHZ_CHANGED"),
        @JsonSubTypes.Type(value = UserCreatedEvent.class,      name = "USER_CREATED"),
        @JsonSubTypes.Type(value = UserUpdatedEvent.class,      name = "USER_UPDATED"),
        @JsonSubTypes.Type(value = UserDeletedEvent.class,      name = "USER_DELETED"),
})
public abstract class PlatformEvent {

    /** Unique event ID for idempotency checks. */
    private UUID eventId = UUID.randomUUID();

    /** ISO-8601 timestamp when the event was created. */
    private Instant occurredAt = Instant.now();

    /** The service that emitted this event. */
    private String source = "platform-service";

    /** Logical event type name (also used as Jackson discriminator). */
    public abstract String getEventType();
}
