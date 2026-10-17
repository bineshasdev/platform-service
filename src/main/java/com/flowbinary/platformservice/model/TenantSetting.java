package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-level override for a setting. Takes precedence over app_settings.
 * Maps to: tenant_settings
 */
@Entity
@Table(name = "tenant_settings",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenant_setting_key",
                columnNames = {"tenant_id", "key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key", referencedColumnName = "key", nullable = false)
    private SettingDefinition definition;

    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
