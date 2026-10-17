package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A single legal / licensing credential supplied by a tenant for verification.
 * A tenant has many of these; the required set depends on {@link TenantType}
 * and country.
 * Maps to: tenant_credentials
 */
@Entity
@Table(name = "tenant_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    private CredentialType type;

    /** ISO 3166-1 alpha-2 country that issued the credential. */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "credential_number", length = 120)
    private String credentialNumber;

    @Column(name = "issuing_authority", length = 200)
    private String issuingAuthority;

    /** Name of the entity / person the credential is issued to. */
    @Column(name = "holder_name", length = 200)
    private String holderName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CredentialStatus status = CredentialStatus.SUBMITTED;

    /** Scan / PDF of the certificate, uploaded via the file-storage API as a DOCUMENT. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_file_id")
    private TenantFile documentFile;

    /** Keycloak user UUID of the reviewer. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    /** Country- or type-specific extra fields (e.g. OTHER credential description). */
    @Type(JsonBinaryType.class)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;
}
