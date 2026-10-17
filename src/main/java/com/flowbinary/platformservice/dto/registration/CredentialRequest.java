package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.model.CredentialType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Payload for POST /api/v1/registration/{tenantId}/credentials.
 *
 * <p>The certificate scan is uploaded first via the file-storage API
 * ({@code POST /api/v1/files}, fileType {@code DOCUMENT}); its id is passed here
 * as {@code documentFileId}.
 */
@Getter
@Setter
public class CredentialRequest {

    @NotNull(message = "Credential type is required")
    private CredentialType type;

    @Size(min = 2, max = 2)
    private String country;

    @Size(max = 120)
    private String credentialNumber;

    @Size(max = 200)
    private String issuingAuthority;

    @Size(max = 200)
    private String holderName;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    /** Id of a previously uploaded DOCUMENT file belonging to this tenant. */
    private UUID documentFileId;

    /** Extra fields — required description when {@code type == OTHER}. */
    private Map<String, Object> attributes;
}
