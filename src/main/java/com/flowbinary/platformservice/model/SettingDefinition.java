package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Declares an available setting with its type, scope, and constraints.
 * Maps to: setting_definitions
 */
@Entity
@Table(name = "setting_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Unique setting key, e.g. "appearance.theme" — also used as FK in setting value tables.
     */
    @Column(name = "key", nullable = false, unique = true)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private SettingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private SettingDataType dataType;

    @Column(name = "default_value")
    private String defaultValue;

    /**
     * For ENUM data type: list of valid values. For INTEGER: {"min":0,"max":100}.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "allowed_values", columnDefinition = "jsonb")
    private List<String> allowedValues;

    @Column(name = "tenant_overridable", nullable = false)
    @Builder.Default
    private Boolean tenantOverridable = true;

    @Column(name = "user_overridable", nullable = false)
    @Builder.Default
    private Boolean userOverridable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    @Builder.Default
    private SettingScope scope = SettingScope.ALL;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
