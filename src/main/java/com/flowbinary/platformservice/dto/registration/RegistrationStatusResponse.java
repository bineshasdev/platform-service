package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.model.TenantType;
import com.flowbinary.platformservice.model.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding / verification progress for a tenant.
 */
@Getter
@Builder
public class RegistrationStatusResponse {

    private UUID tenantId;
    private String slug;
    private TenantType type;
    private VerificationStatus verificationStatus;
    private boolean listed;
    private boolean emailVerified;
    private Instant verifiedAt;

    private boolean legalProfileProvided;
    private int credentialsSubmitted;
    private int credentialsVerified;

    /** What still needs to happen before the tenant can be verified / listed. */
    private List<String> outstanding;
}
