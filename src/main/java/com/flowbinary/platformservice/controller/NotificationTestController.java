package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platform.event.avro.AudienceScope;
import com.flowbinary.platform.event.avro.EventAudience;
import com.flowbinary.platform.event.avro.NotificationEvent;
import com.flowbinary.platformservice.config.AppProperties;
import com.flowbinary.platformservice.dto.test.NotificationTestRequest;
import com.flowbinary.platformservice.dto.test.NotificationTestResponse;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only endpoint for firing synthetic {@link NotificationEvent} records directly
 * onto {@code platform.notification.events} without going through a real user/tenant
 * lifecycle operation.
 * <p>
 * Restricted to platform operators ({@code platform.tenant.manage}).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/notifications")
@RequiresPrivilege("platform.tenant.manage")
@Tag(name = "Test", description = "Developer / QA test utilities")
public class NotificationTestController {

    private final KafkaTemplate<String, SpecificRecord> avroKafkaTemplate;
    private final AppProperties                         appProperties;
    private final TenantContextResolver                 tenantContextResolver;

    public NotificationTestController(
            @Qualifier("avroKafkaTemplate") KafkaTemplate<String, SpecificRecord> avroKafkaTemplate,
            AppProperties appProperties,
            TenantContextResolver tenantContextResolver) {
        this.avroKafkaTemplate     = avroKafkaTemplate;
        this.appProperties         = appProperties;
        this.tenantContextResolver = tenantContextResolver;
    }

    /**
     * Publishes a synthetic {@link NotificationEvent} to {@code platform.notification.events}.
     *
     * <p>Example request body:
     * <pre>{@code
     * {
     *   "eventType": "USER_CREATED",
     *   "scope": "USER",
     *   "userIds": ["<user-uuid>"],
     *   "data": {
     *     "firstName": "Jane",
     *     "lastName":  "Smith",
     *     "email":     "jane@acme.com",
     *     "role":      "TENANT_ADMIN"
     *   }
     * }
     * }</pre>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Publish a synthetic notification event to Kafka (SUPERADMIN only)")
    public NotificationTestResponse publish(
            Authentication authentication,
            @Valid @RequestBody NotificationTestRequest request) {

        // Resolve tenantId: prefer explicit value in body, fall back to caller's tenant
        String tenantId = request.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            Tenant tenant = tenantContextResolver.resolveRequired(authentication);
            tenantId = tenant.getId().toString();
        }

        AudienceScope scope = parseScope(request.scope());
        List<String>  userIds = request.userIds() != null ? request.userIds() : List.of();
        // Avro's `data` map is typed {values: "string"} with no null union — a caller-supplied
        // null value would otherwise fail serialization only once the async Kafka send runs.
        Map<String, String> data = sanitize(request.data());

        EventAudience audience = EventAudience.newBuilder()
                .setTenantId(tenantId)
                .setScope(scope)
                .setUserIds(userIds)
                .build();

        String eventId = UUID.randomUUID().toString();
        NotificationEvent event = NotificationEvent.newBuilder()
                .setEventId(eventId)
                .setEventType(request.eventType())
                .setOccurredAt(Instant.now().toString())
                .setSource("platform-service-test")
                .setAudience(audience)
                .setData(data)
                .setIdempotencyKey(null)
                .build();

        String topic = appProperties.getKafka().getTopics().getNotificationEvents();
        avroKafkaTemplate.send(topic, tenantId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Test notification publish failed [{}]: {}", request.eventType(), ex.getMessage(), ex);
                    } else {
                        log.info("Test notification published [{}] → topic={} partition={} offset={}",
                                request.eventType(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        return new NotificationTestResponse(
                eventId,
                request.eventType(),
                topic,
                tenantId,
                scope.name(),
                userIds,
                data,
                "PUBLISHED"
        );
    }

    private static Map<String, String> sanitize(Map<String, String> data) {
        if (data == null) {
            return Map.of();
        }
        Map<String, String> sanitized = new HashMap<>();
        data.forEach((key, value) -> sanitized.put(key, value == null ? "" : value));
        return sanitized;
    }

    private static AudienceScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) return AudienceScope.TENANT;
        try {
            return AudienceScope.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid scope '" + raw + "'. Must be USER, TENANT, or SYSTEM.");
        }
    }
}
