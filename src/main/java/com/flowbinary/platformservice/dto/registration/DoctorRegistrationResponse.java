package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.model.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for POST /api/v1/registration/doctors.
 */
@Getter
@Builder
public class DoctorRegistrationResponse {

    private UUID doctorId;
    private String keycloakUserId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    private VerificationStatus verificationStatus;
    private boolean listed;
    private Instant createdAt;

    private List<String> nextSteps;
}
