package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.registration.DoctorRegistrationRequest;
import com.flowbinary.platformservice.dto.registration.DoctorRegistrationResponse;
import com.flowbinary.platformservice.service.DoctorRegistrationService;
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
 * Practitioner self-signup. Public endpoint (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/registration/doctors")
@RequiredArgsConstructor
@Tag(name = "Registration — Doctor", description = "Practitioner signup")
public class DoctorRegistrationController {

    private final DoctorRegistrationService doctorRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register as a doctor",
            description = "Creates a platform-wide practitioner profile and login. The profile is "
                    + "unverified and not listed in search until council credentials are reviewed.")
    @ApiResponse(responseCode = "201", description = "Doctor registered")
    @ApiResponse(responseCode = "400", description = "Validation error or email/mobile already registered")
    DoctorRegistrationResponse register(@Valid @RequestBody DoctorRegistrationRequest request) {
        return doctorRegistrationService.register(request);
    }
}
