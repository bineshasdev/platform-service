package com.flowbinary.platformservice.dto.registration;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for POST /api/v1/registration/patients.
 */
@Getter
@Builder
public class PatientRegistrationResponse {

    private UUID patientId;
    private String keycloakUserId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private boolean emailVerified;
    private boolean phoneVerified;
    private Instant createdAt;
}
