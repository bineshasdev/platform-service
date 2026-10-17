package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.ClinicEmailChangeRequest;
import com.flowbinary.platformservice.dto.registration.ClinicEmailVerifyRequest;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.ClinicRegistrationResponse;
import com.flowbinary.platformservice.dto.registration.EmailVerificationResponse;
import com.flowbinary.platformservice.service.ClinicEmailVerificationService;
import com.flowbinary.platformservice.service.ClinicRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clinic signup. Behind the trusted-client gate — the web/BFF calls it with a
 * service token, same as the legacy tenant signup.
 */
@RestController
@RequestMapping("/api/v1/registration/clinics")
@RequiredArgsConstructor
@Tag(name = "Registration — Clinic", description = "Clinic / practice signup")
public class ClinicRegistrationController {

    private final ClinicRegistrationService clinicRegistrationService;
    private final ClinicEmailVerificationService emailVerificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "Register a new clinic",
            description = "Creates the Keycloak organisation, the first admin user, the tenant, the "
                    + "clinic profile and a free-plan subscription. Starts PENDING verification, not listed publicly.")
    @ApiResponse(responseCode = "201", description = "Clinic registered")
    @ApiResponse(responseCode = "400", description = "Validation error or email already registered")
    ClinicRegistrationResponse register(@Valid @RequestBody ClinicRegistrationRequest request) {
        return clinicRegistrationService.register(request);
    }

    @PostMapping("/{tenantId}/email/verify")
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "Verify the clinic admin email with the code that was sent",
            description = "Idempotent once verified. Wrong codes count against a 5-attempt cap per code.")
    @ApiResponse(responseCode = "200", description = "Verified (or already verified)")
    @ApiResponse(responseCode = "400", description = "Incorrect, expired or exhausted code")
    EmailVerificationResponse verifyEmail(@PathVariable UUID tenantId,
                                          @Valid @RequestBody ClinicEmailVerifyRequest request) {
        return emailVerificationService.verify(tenantId, request.getOtp());
    }

    @PostMapping("/{tenantId}/email/resend")
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "Send a new email verification code",
            description = "Invalidates any earlier code. 60-second cooldown and 3 sends per 15 minutes.")
    @ApiResponse(responseCode = "200", description = "Code sent (or already verified)")
    @ApiResponse(responseCode = "429", description = "Cooldown or rate limit hit")
    EmailVerificationResponse resendEmailCode(@PathVariable UUID tenantId) {
        return emailVerificationService.resend(tenantId);
    }

    @PostMapping("/{tenantId}/email/change")
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "Correct a mistyped signup email and send a code to the new address",
            description = "Only while the email is still unverified.")
    @ApiResponse(responseCode = "200", description = "Email updated and code sent")
    @ApiResponse(responseCode = "400", description = "Already verified, or email already registered")
    EmailVerificationResponse changeEmail(@PathVariable UUID tenantId,
                                          @Valid @RequestBody ClinicEmailChangeRequest request) {
        return emailVerificationService.changeEmail(tenantId, request.getEmail());
    }
}
