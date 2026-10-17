package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.ResolveTenantResponse;
import com.flowbinary.platformservice.dto.TenantSignupRequest;
import com.flowbinary.platformservice.dto.TenantSignupResponse;
import com.flowbinary.platformservice.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant lifecycle management")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(
        summary  = "Register a new tenant",
        description = """
            Creates a Keycloak organisation for the tenant, provisions an OWNER admin user,
            and assigns the free plan. Returns the tenant and admin identifiers.
            """
    )
    @ApiResponse(responseCode = "201", description = "Tenant created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Slug or email already in use")
    TenantSignupResponse signup(@Valid @RequestBody TenantSignupRequest request) {
        return tenantService.signup(request);
    }

    @GetMapping("/resolve")
    @Operation(
        summary = "Resolve tenant by email or slug",
        description = "Returns Keycloak realm and client details for the tenant matching the given email or slug."
    )
    @ApiResponse(responseCode = "200", description = "Tenant resolved")
    @ApiResponse(responseCode = "400", description = "Tenant not found")
    ResolveTenantResponse resolve(@RequestParam String identifier) {
        return tenantService.resolveTenant(identifier);
    }
}
