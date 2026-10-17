package com.flowbinary.platformservice.dto.registration;

import com.flowbinary.platformservice.model.CredentialStatus;
import com.flowbinary.platformservice.model.CredentialType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * View of a single tenant credential.
 */
@Getter
@Builder
public class CredentialResponse {

    private UUID id;
    private UUID tenantId;
    private CredentialType type;
    private String country;
    private String credentialNumber;
    private String issuingAuthority;
    private String holderName;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private CredentialStatus status;
    private UUID documentFileId;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNotes;
    private Map<String, Object> attributes;
    private Instant createdAt;
    private Instant updatedAt;
}
