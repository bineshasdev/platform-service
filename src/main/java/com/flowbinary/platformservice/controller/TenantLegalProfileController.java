package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.LegalProfileRequest;
import com.flowbinary.platformservice.dto.registration.LegalProfileResponse;
import com.flowbinary.platformservice.service.TenantLegalProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * A tenant's legal identity — the registered entity licences are issued to.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/legal-profile")
@RequiredArgsConstructor
@Tag(name = "Tenant legal profile", description = "Registered legal entity details")
public class TenantLegalProfileController {

    private final TenantLegalProfileService legalProfileService;

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create or update the tenant's legal profile")
    LegalProfileResponse upsert(@PathVariable UUID tenantId,
                                @Valid @RequestBody LegalProfileRequest request) {
        return legalProfileService.upsert(tenantId, request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch the tenant's legal profile")
    LegalProfileResponse get(@PathVariable UUID tenantId) {
        return legalProfileService.get(tenantId);
    }
}
