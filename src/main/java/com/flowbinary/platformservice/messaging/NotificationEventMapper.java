package com.flowbinary.platformservice.messaging;

import com.flowbinary.platform.event.avro.AudienceScope;
import com.flowbinary.platform.event.avro.EventAudience;
import com.flowbinary.platform.event.avro.NotificationEvent;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps every {@link PlatformEvent} subtype to the Avro {@link NotificationEvent} envelope
 * that is published to {@code platform.notification.events}.
 * <p>
 * All events share the same schema:
 * <ul>
 *   <li>{@code eventType} — the logical event name consumed by notification-service</li>
 *   <li>{@code audience} — who should receive the resulting notification</li>
 *   <li>{@code data} — flat string map used for Handlebars template rendering</li>
 * </ul>
 */
final class NotificationEventMapper {

    /** Placeholder tenantId for system-scoped events that have no specific tenant. */
    private static final String SYSTEM_TENANT  = "SYSTEM";

    // ── Repeated data-map keys ─────────────────────────────────────────────
    private static final String KEY_TENANT_ID   = "tenantId";
    private static final String KEY_EMAIL       = "email";
    private static final String KEY_USER_ID     = "userId";
    private static final String KEY_FIRST_NAME  = "firstName";
    private static final String KEY_LAST_NAME   = "lastName";
    private static final String KEY_ROLE        = "role";

    private NotificationEventMapper() {}

    // ── Generic dispatcher ─────────────────────────────────────────────────

    static NotificationEvent toNotificationEvent(PlatformEvent evt) {
        if (evt instanceof TenantCreatedEvent e)    return forTenantCreated(e);
        if (evt instanceof ClinicCreatedEvent e)    return forClinicCreated(e);
        if (evt instanceof TenantPlanChangedEvent e) return forTenantPlanChanged(e);
        if (evt instanceof TenantVerificationChangedEvent e) return forTenantVerificationChanged(e);
        if (evt instanceof DoctorRegisteredEvent e)  return forDoctorRegistered(e);
        if (evt instanceof PatientRegisteredEvent e) return forPatientRegistered(e);
        if (evt instanceof FeatureToggledEvent e)   return forFeatureToggled(e);
        if (evt instanceof MenuItemUpdatedEvent e)  return forMenuItemUpdated(e);
        if (evt instanceof UserCreatedEvent e)      return forUserCreated(e);
        if (evt instanceof UserUpdatedEvent e)      return forUserUpdated(e);
        if (evt instanceof UserDeletedEvent e)      return forUserDeleted(e);
        throw new IllegalArgumentException(
                "No Avro mapping defined for event type: " + evt.getEventType());
    }

    // ── Tenant events ──────────────────────────────────────────────────────

    static NotificationEvent forTenantCreated(TenantCreatedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_TENANT_ID, evt.getTenantId());
        put(data, "tenantName",  evt.getTenantName());
        put(data, "slug",        evt.getSlug());
        put(data, KEY_EMAIL,     evt.getEmail());
        put(data, "plan",        evt.getPlan());
        put(data, "type",        evt.getType());
        put(data, "verificationStatus", evt.getVerificationStatus());

        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    static NotificationEvent forClinicCreated(ClinicCreatedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_TENANT_ID,  evt.getTenantId());
        put(data, "clinicId",     evt.getClinicId());
        put(data, "clinicName",   evt.getClinicName());
        put(data, "displayName",  evt.getDisplayName());
        put(data, "slug",         evt.getSlug());
        put(data, KEY_EMAIL,      evt.getEmail());
        put(data, "plan",         evt.getPlan());
        put(data, "type",         evt.getType());
        put(data, "verificationStatus", evt.getVerificationStatus());

        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    static NotificationEvent forTenantPlanChanged(TenantPlanChangedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_TENANT_ID,   evt.getTenantId());
        put(data, "previousPlan",  evt.getPreviousPlan());
        put(data, "newPlan",       evt.getNewPlan());
        put(data, "changedBy",     evt.getChangedBy());

        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    static NotificationEvent forTenantVerificationChanged(TenantVerificationChangedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_TENANT_ID,    evt.getTenantId());
        put(data, "tenantName",     evt.getTenantName());
        put(data, "slug",           evt.getSlug());
        put(data, "type",           evt.getType());
        put(data, "previousStatus", evt.getPreviousStatus());
        put(data, "newStatus",      evt.getNewStatus());
        put(data, "listed",         String.valueOf(evt.isListed()));
        put(data, "changedBy",      evt.getChangedBy());

        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    // ── Practitioner / patient events (not tenant-scoped) ──────────────────

    static NotificationEvent forDoctorRegistered(DoctorRegisteredEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_USER_ID,    evt.getAuthUserId());
        put(data, "doctorId",     evt.getDoctorId());
        put(data, KEY_EMAIL,      evt.getEmail());
        put(data, KEY_FIRST_NAME, evt.getFirstName());
        put(data, KEY_LAST_NAME,  evt.getLastName());

        return build(evt, systemAudience(), data);
    }

    static NotificationEvent forPatientRegistered(PatientRegisteredEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_USER_ID,    evt.getAuthUserId());
        put(data, "patientId",    evt.getPatientId());
        put(data, KEY_EMAIL,      evt.getEmail());
        put(data, KEY_FIRST_NAME, evt.getFirstName());
        put(data, KEY_LAST_NAME,  evt.getLastName());

        return build(evt, systemAudience(), data);
    }

    // ── Feature event ──────────────────────────────────────────────────────

    static NotificationEvent forFeatureToggled(FeatureToggledEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_TENANT_ID,  evt.getTenantId());
        put(data, "featureCode",  evt.getFeatureCode());
        put(data, "enabled",      String.valueOf(evt.isEnabled()));
        put(data, "toggledBy",    evt.getToggledBy());

        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    // ── Menu event ─────────────────────────────────────────────────────────

    static NotificationEvent forMenuItemUpdated(MenuItemUpdatedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, "menuItemId", evt.getMenuItemId());
        put(data, "code",       evt.getCode());
        put(data, "action",     evt.getAction());

        // Menu items are platform-wide — no specific tenant
        EventAudience audience = EventAudience.newBuilder()
                .setTenantId(SYSTEM_TENANT)
                .setScope(AudienceScope.SYSTEM)
                .setUserIds(List.of())
                .build();
        return build(evt, audience, data);
    }

    // ── User events ────────────────────────────────────────────────────────

    static NotificationEvent forUserCreated(UserCreatedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_USER_ID,   evt.getUserId());
        put(data, KEY_EMAIL,     evt.getEmail());
        put(data, KEY_FIRST_NAME, evt.getFirstName());
        put(data, KEY_LAST_NAME,  evt.getLastName());
        put(data, KEY_ROLE,      evt.getRole());
        put(data, KEY_TENANT_ID, evt.getTenantId());

        EventAudience audience = userAudience(
                evt.getTenantId().toString(),
                evt.getUserId() != null ? List.of(evt.getUserId().toString()) : List.of());
        return build(evt, audience, data);
    }

    static NotificationEvent forUserUpdated(UserUpdatedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_USER_ID,    evt.getUserId());
        put(data, KEY_EMAIL,      evt.getEmail());
        put(data, KEY_FIRST_NAME, evt.getFirstName());
        put(data, KEY_LAST_NAME,  evt.getLastName());
        put(data, KEY_ROLE,       evt.getRole());
        put(data, "status",       evt.getStatus());
        put(data, KEY_TENANT_ID,  evt.getTenantId());
        put(data, "changedFields", evt.getChangedFields() != null
                ? String.join(",", evt.getChangedFields()) : "");

        EventAudience audience = userAudience(
                evt.getTenantId().toString(),
                evt.getUserId() != null ? List.of(evt.getUserId().toString()) : List.of());
        return build(evt, audience, data);
    }

    static NotificationEvent forUserDeleted(UserDeletedEvent evt) {
        Map<String, String> data = new HashMap<>();
        put(data, KEY_USER_ID,   evt.getUserId());
        put(data, KEY_EMAIL,     evt.getEmail());
        put(data, KEY_FIRST_NAME, evt.getFirstName());
        put(data, KEY_LAST_NAME,  evt.getLastName());
        put(data, KEY_ROLE,      evt.getRole());
        put(data, KEY_TENANT_ID, evt.getTenantId());

        // The deleted user can no longer receive notifications — notify tenant admins
        EventAudience audience = tenantAudience(evt.getTenantId().toString());
        return build(evt, audience, data);
    }

    // ── Audience factories ─────────────────────────────────────────────────

    private static EventAudience systemAudience() {
        return EventAudience.newBuilder()
                .setTenantId(SYSTEM_TENANT)
                .setScope(AudienceScope.SYSTEM)
                .setUserIds(List.of())
                .build();
    }

    private static EventAudience tenantAudience(String tenantId) {
        return EventAudience.newBuilder()
                .setTenantId(tenantId)
                .setScope(AudienceScope.TENANT)
                .setUserIds(List.of())
                .build();
    }

    private static EventAudience userAudience(String tenantId, List<String> userIds) {
        return EventAudience.newBuilder()
                .setTenantId(tenantId)
                .setScope(AudienceScope.USER)
                .setUserIds(userIds)
                .build();
    }

    // ── Build helper ───────────────────────────────────────────────────────

    private static NotificationEvent build(PlatformEvent evt,
                                           EventAudience audience,
                                           Map<String, String> data) {
        // @SuperBuilder does not apply class-level field initializers — guard each base field
        String eventId    = evt.getEventId()    != null ? evt.getEventId().toString()    : java.util.UUID.randomUUID().toString();
        String occurredAt = evt.getOccurredAt() != null ? evt.getOccurredAt().toString() : java.time.Instant.now().toString();
        String source     = evt.getSource()     != null ? evt.getSource()               : "platform-service";

        return NotificationEvent.newBuilder()
                .setEventId(eventId)
                .setEventType(evt.getEventType())
                .setOccurredAt(occurredAt)
                .setSource(source)
                .setAudience(audience)
                .setData(data)
                .setIdempotencyKey(null)
                .build();
    }

    /** Puts a non-null value as a string into the data map; stores empty string for nulls. */
    private static void put(Map<String, String> map, String key, Object value) {
        map.put(key, Optional.ofNullable(value).map(Object::toString).filter(s -> !s.isBlank()).orElse(""));
    }
}
