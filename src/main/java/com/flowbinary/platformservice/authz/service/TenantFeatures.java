package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.model.Plan;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantPlan;
import com.flowbinary.platformservice.model.TenantSubscription;
import com.flowbinary.platformservice.repository.PlanFeatureRepository;
import com.flowbinary.platformservice.repository.PlanRepository;
import com.flowbinary.platformservice.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plan features enabled for a tenant: the active subscription's plan, or the
 * tenant's own plan code when it has no subscription row.
 */
@Component
@RequiredArgsConstructor
public class TenantFeatures {

    /** Creating and editing roles derived from templates. */
    public static final String DERIVED_ROLES = "authz.derived_roles";
    /** Creating and editing roles built from scratch. */
    public static final String CUSTOM_ROLES = "authz.custom_roles";

    private final TenantSubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PlanFeatureRepository planFeatures;

    @Transactional(readOnly = true)
    public Set<String> enabled(Tenant tenant) {
        Plan plan = subscriptions.findActiveByTenantId(tenant.getId())
                .map(TenantSubscription::getPlan)
                .or(() -> plans.findByCode(tenant.getPlan() == null ? TenantPlan.free : tenant.getPlan()))
                .orElse(null);
        if (plan == null) return Set.of();
        return planFeatures.findByIdPlanId(plan.getId()).stream()
                .filter(pf -> Boolean.TRUE.equals(pf.getEnabled()))
                .map(pf -> pf.getFeature().getCode())
                .collect(Collectors.toUnmodifiableSet());
    }
}
