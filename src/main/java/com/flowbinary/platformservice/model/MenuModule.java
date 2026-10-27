package com.flowbinary.platformservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A service that contributes menu items. The platform trusts nothing in a
 * manifest beyond what this registration allows: the route prefixes and
 * privilege namespace are set by an operator, and only the owner client's
 * service token may push.
 * Maps to: menu_modules
 */
@Entity
@Table(name = "menu_modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuModule extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** Keycloak client whose client-credentials token may push this module's manifest. */
    @Column(name = "owner_client_id", nullable = false)
    private String ownerClientId;

    @Column(name = "manifest_url")
    private String manifestUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_route_prefixes", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> allowedRoutePrefixes = new ArrayList<>();

    @Column(name = "privilege_module_code")
    private String privilegeModuleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ModuleStatus status = ModuleStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "health", nullable = false)
    @Builder.Default
    private ModuleHealth health = ModuleHealth.UNKNOWN;

    @Column(name = "current_version")
    private String currentVersion;

    @Column(name = "current_hash")
    private String currentHash;

    @Type(JsonBinaryType.class)
    @Column(name = "manifest", columnDefinition = "jsonb")
    private JsonNode manifest;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
