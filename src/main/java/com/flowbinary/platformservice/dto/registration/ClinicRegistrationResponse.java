package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.model.TenantType;
import com.flowbinary.platformservice.model.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for POST /api/v1/registration/clinics.
 */
@Getter
@Builder
public class ClinicRegistrationResponse {

    private UUID tenantId;
    private UUID clinicId;
    private String clinicName;
    private String displayName;
    private String slug;
    private TenantType type;

    /** Always {@link VerificationStatus#PENDING} straight after signup. */
    private VerificationStatus verificationStatus;

    /** Always {@code false} until credentials are verified. */
    private boolean listed;

    /** Always {@code false} straight after signup — confirm via the email verification endpoints. */
    private boolean emailVerified;

    private String keycloakOrgId;
    private UUID adminUserId;
    private String adminUsername;
    private String keycloakUserId;
    private String plan;
    private Instant createdAt;

    /** Human-readable checklist the UI can render to guide the clinic to verification. */
    private List<String> nextSteps;
}
