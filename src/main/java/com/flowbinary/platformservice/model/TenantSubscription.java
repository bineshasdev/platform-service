package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Active subscription record linking a tenant to a plan.
 * Maps to: tenant_subscriptions
 */
@Entity
@Table(name = "tenant_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false)
    @Builder.Default
    private Instant currentPeriodStart = Instant.now();

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    /**
     * Stripe or other payment provider subscription ID.
     */
    @Column(name = "payment_provider_id")
    private String paymentProviderId;

    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private Boolean cancelAtPeriodEnd = false;
}
