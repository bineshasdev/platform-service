package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks how much of a feature a tenant has consumed in a given period.
 * Maps to: usage_records
 */
@Entity
@Table(name = "usage_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_usage_tenant_feature_period",
                columnNames = {"tenant_id", "feature_id", "period"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;

    /**
     * Billing period in YYYY-MM format, e.g. "2026-03".
     */
    @Column(name = "period", nullable = false)
    private String period;

    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private Instant lastUpdated = Instant.now();
}
