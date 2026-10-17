package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Defines which features are available on each plan and at what limits.
 * Maps to: plan_features (composite PK)
 */
@Entity
@Table(name = "plan_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeature {

    @EmbeddedId
    private PlanFeatureId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("planId")
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("featureId")
    @JoinColumn(name = "feature_id")
    private Feature feature;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * NULL means unlimited.
     */
    @Column(name = "usage_limit")
    private Long usageLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_period")
    private ResetPeriod resetPeriod;
}
