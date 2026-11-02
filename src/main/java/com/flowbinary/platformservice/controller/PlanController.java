package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platformservice.dto.plan.PlanResponse;
import com.flowbinary.platformservice.dto.plan.UpgradeRequest;
import com.flowbinary.platformservice.dto.plan.UpgradeResponse;
import com.flowbinary.platformservice.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Subscription plan management")
public class PlanController {

    private final PlanService planService;

    /**
     * GET /api/v1/plans
     * Lists all active plans with features — public, no auth required.
     */
    @GetMapping
    @Operation(summary = "List all active subscription plans")
    List<PlanResponse> listPlans() {
        return planService.listActivePlans();
    }

    /**
     * POST /api/v1/plans/upgrade
     * Upgrades the authenticated tenant's subscription to the target plan.
     */
    @PostMapping("/upgrade")
    @RequiresPrivilege("platform.subscription.manage")
    @Operation(summary = "Upgrade or change the tenant's subscription plan")
    UpgradeResponse upgrade(Authentication authentication,
                            @Valid @RequestBody UpgradeRequest request) {
        return planService.upgradePlan(authentication, request.getTargetPlan());
    }
}
