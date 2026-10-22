package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the platform's append-only audit trail. Shared by every module;
 * {@code entityType} namespaces the rows (menu rows use {@code MENU_*}).
 * The table rejects UPDATE and DELETE by trigger.
 * Maps to: audit_log
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, updatable = false)
    @Builder.Default
    private String actorType = "USER";

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @Type(JsonBinaryType.class)
    @Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
    private Object beforeState;

    @Type(JsonBinaryType.class)
    @Column(name = "after_state", columnDefinition = "jsonb", updatable = false)
    private Object afterState;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(name = "source", nullable = false, updatable = false)
    @Builder.Default
    private String source = "API";

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    @Builder.Default
    private Map<String, Object> metadata = new java.util.HashMap<>();
}
