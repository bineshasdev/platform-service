package com.flowbinary.platformservice.dto.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload for the reviewer endpoints:
 * <ul>
 *   <li>POST /api/v1/registration/{tenantId}/credentials/{credentialId}/review</li>
 *   <li>POST /api/v1/registration/{tenantId}/decision</li>
 * </ul>
 */
@Getter
@Setter
public class VerificationDecisionRequest {

    @NotNull(message = "decision is required")
    private Decision decision;

    /** Reviewer notes — required on REJECT so the tenant knows what to fix. */
    @Size(max = 2000)
    private String notes;
}
