package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.PatientRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.PatientRegistrationResponse;
import com.flowbinary.platformservice.service.PatientRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient self-signup. Public endpoint (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/registration/patients")
@RequiredArgsConstructor
@Tag(name = "Registration — Patient", description = "Patient signup")
public class PatientRegistrationController {

    private final PatientRegistrationService patientRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as a patient",
            description = "Creates a platform-global patient account and login. Email / mobile start "
                    + "unverified — confirm them via the OTP endpoints.")
    @ApiResponse(responseCode = "201", description = "Patient registered")
    @ApiResponse(responseCode = "400", description = "Validation error or email/mobile already registered")
    PatientRegistrationResponse register(@Valid @RequestBody PatientRegistrationRequest request) {
        return patientRegistrationService.register(request);
    }
}
