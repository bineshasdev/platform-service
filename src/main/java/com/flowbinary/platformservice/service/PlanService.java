package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.dto.plan.*;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import com.flowbinary.platformservice.security.TenantContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository               planRepository;
    private final PlanFeatureRepository        planFeatureRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final TenantContextResolver        tenantContextResolver;

    /** Returns all active plans with their features, ordered by price ascending. */
    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planRepository.findByIsActiveTrue().stream()
                .sorted((a, b) -> a.getPrice().compareTo(b.getPrice()))
                .map(this::toPlanResponse)
                .toList();
    }

    /**
     * Upgrades (or downgrades) the calling tenant's subscription to the target plan.
     * The existing ACTIVE/TRIAL subscription is cancelled and a new ACTIVE one is created.
     */
    @Transactional
    public UpgradeResponse upgradePlan(Authentication authentication, TenantPlan targetPlan) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);

        Plan target = planRepository.findByCode(targetPlan)
                .orElseThrow(() -> new NoSuchElementException("Plan not found: " + targetPlan));

        TenantSubscription current = subscriptionRepository
                .findActiveByTenantId(tenant.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "No active subscription for tenant: " + tenant.getSlug()));

        String previousPlan = current.getPlan().getCode().name();

        if (previousPlan.equals(targetPlan.name())) {
            throw new IllegalArgumentException("Tenant is already on plan: " + targetPlan);
        }

        // Cancel existing subscription
        current.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(current);

        // Create new subscription
        TenantSubscription upgraded = TenantSubscription.builder()
                .tenant(tenant)
                .plan(target)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .build();
        upgraded = subscriptionRepository.save(upgraded);

        // Update tenant.plan cache field
        tenant.setPlan(targetPlan);

        return UpgradeResponse.builder()
                .subscriptionId(upgraded.getId())
                .previousPlan(previousPlan)
                .newPlan(targetPlan.name())
                .status(upgraded.getStatus().name())
                .effectiveFrom(upgraded.getCurrentPeriodStart())
                .build();
    }

    // ── mapping ────────────────────────────────────────────────────────────

    private PlanResponse toPlanResponse(Plan plan) {
        List<PlanFeatureDetail> features = planFeatureRepository.findByIdPlanId(plan.getId())
                .stream()
                .map(pf -> PlanFeatureDetail.builder()
                        .featureCode(pf.getFeature().getCode())
                        .featureName(pf.getFeature().getName())
                        .category(pf.getFeature().getCategory().name())
                        .enabled(pf.getEnabled())
                        .usageLimit(pf.getUsageLimit())
                        .build())
                .toList();

        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .code(plan.getCode().name())
                .price(plan.getPrice())
                .billingCycle(plan.getBillingCycle() != null ? plan.getBillingCycle().name() : null)
                .features(features)
                .build();
    }
}
