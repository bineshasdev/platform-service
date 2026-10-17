package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.RegistrationStatusResponse;
import com.flowbinary.platformservice.dto.registration.VerificationDecisionRequest;
import com.flowbinary.platformservice.service.TenantVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * A tenant's verification lifecycle — {@code PENDING → IN_REVIEW → VERIFIED / REJECTED} —
 * and its resulting public-listing state.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/verification")
@RequiredArgsConstructor
@Tag(name = "Tenant verification", description = "Credential review and public listing")
public class TenantVerificationController {

    private final TenantVerificationService verificationService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verification / onboarding progress for a tenant")
    RegistrationStatusResponse status(@PathVariable UUID tenantId) {
        return verificationService.getStatus(tenantId);
    }

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit the tenant's legal profile + credentials for platform review")
    RegistrationStatusResponse submitForReview(@PathVariable UUID tenantId) {
        return verificationService.submitForReview(tenantId);
    }

    @PostMapping("/decision")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Final verification decision — APPROVE lists the tenant in public search")
    RegistrationStatusResponse decide(@PathVariable UUID tenantId,
                                      @Valid @RequestBody VerificationDecisionRequest request) {
        return verificationService.decide(tenantId, request);
    }
}
