package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.ContactChangeRequest;
import com.flowbinary.platformservice.dto.registration.ContactOtpRequest;
import com.flowbinary.platformservice.dto.registration.DoctorContactVerificationStatus;
import com.flowbinary.platformservice.service.DoctorContactVerificationService;
import com.flowbinary.platformservice.service.DoctorContactVerificationService.Channel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The signed-in practitioner's own email / mobile verification. Called by the web app
 * with the user's token; the profile is always the token's subject.
 */
@RestController
@RequestMapping("/api/v1/doctors/me/contact-verification")
@RequiredArgsConstructor
@Tag(name = "Doctor — Contact verification", description = "Email and mobile ownership checks at sign-in")
public class DoctorContactVerificationController {

    private final DoctorContactVerificationService service;

    @GetMapping
    @Operation(summary = "Verification status of the signed-in user",
            description = "required=false when the user has no practitioner profile.")
    DoctorContactVerificationStatus status(@AuthenticationPrincipal Jwt jwt) {
        return service.status(userId(jwt));
    }

    @PostMapping("/{channel}/send")
    @Operation(summary = "Send a verification code to the email or mobile on file",
            description = "channel = email | phone. 60-second cooldown and 3 sends per 15 minutes per target.")
    DoctorContactVerificationStatus send(@AuthenticationPrincipal Jwt jwt, @PathVariable String channel) {
        return service.send(userId(jwt), Channel.parse(channel));
    }

    @PostMapping("/{channel}/verify")
    @Operation(summary = "Confirm the code that was sent",
            description = "Idempotent once verified. Wrong codes count against a 5-attempt cap per code.")
    DoctorContactVerificationStatus verify(@AuthenticationPrincipal Jwt jwt, @PathVariable String channel,
                                           @Valid @RequestBody ContactOtpRequest request) {
        return service.verify(userId(jwt), Channel.parse(channel), request.getOtp());
    }

    @PostMapping("/{channel}/change")
    @Operation(summary = "Correct a mistyped email or mobile and send a code to the new value",
            description = "Only while that channel is unverified.")
    DoctorContactVerificationStatus change(@AuthenticationPrincipal Jwt jwt, @PathVariable String channel,
                                           @Valid @RequestBody ContactChangeRequest request) {
        return service.change(userId(jwt), Channel.parse(channel), request.getValue());
    }

    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Token has no valid subject");
        }
    }
}
