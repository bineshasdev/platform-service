package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.otp.SendOtpRequest;
import com.flowbinary.platformservice.dto.otp.SendOtpResponse;
import com.flowbinary.platformservice.dto.otp.VerifyOtpRequest;
import com.flowbinary.platformservice.dto.otp.VerifyOtpResponse;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
@Tag(name = "OTP", description = "Email and mobile OTP verification")
public class OtpController {

    private final OtpService otpService;
    private final TenantContextResolver tenantContextResolver;
    private final TenantUserRepository tenantUserRepository;

    @PostMapping("/send")
    @Operation(summary = "Send an OTP code to an email address or mobile number",
            description = "Public endpoint — used both for pre-signup contact verification (no token) "
                    + "and by authenticated users verifying/changing their email or mobile.")
    SendOtpResponse sendOtp(Authentication authentication, @Valid @RequestBody SendOtpRequest request) {
        Tenant tenant = resolveTenant(authentication);
        TenantUser user = tenant != null ? resolveUser(tenant, authentication) : null;
        return otpService.sendOtp(request, tenant, user);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an OTP code against a verification token",
            description = "Public endpoint — see /send.")
    VerifyOtpResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return otpService.verifyOtp(request);
    }

    /** Anonymous callers (public /send /verify with no token) get no tenant context. */
    private Tenant resolveTenant(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken)) {
            return null;
        }
        return tenantContextResolver.resolve(authentication).orElse(null);
    }

    private TenantUser resolveUser(Tenant tenant, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            return null;
        }
        Jwt jwt = jwtToken.getToken();
        String sub = jwt.getSubject();
        if (sub == null) {
            return null;
        }
        Optional<TenantUser> user = tenantUserRepository.findByTenantIdAndAuthUserId(tenant.getId(), UUID.fromString(sub));
        return user.orElse(null);
    }
}
