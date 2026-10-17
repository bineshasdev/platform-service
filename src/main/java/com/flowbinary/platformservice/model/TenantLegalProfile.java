package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * Country-aware legal identity of a tenant — the registered entity a licence is
 * issued to. One row per tenant.
 * Maps to: tenant_legal_profiles
 */
@Entity
@Table(name = "tenant_legal_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantLegalProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    /** Registered legal name (may differ from the trading / display name). */
    @Column(name = "legal_name")
    private String legalName;

    /** e.g. PROPRIETORSHIP, PARTNERSHIP, PRIVATE_LIMITED, LLP, TRUST, INDIVIDUAL. */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** Company / establishment registration number. */
    @Column(name = "registration_number", length = 120)
    private String registrationNumber;

    /** Primary tax identifier — GSTIN (IN), VAT (EU), EIN (US), … */
    @Column(name = "tax_id", length = 120)
    private String taxId;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "zip", length = 20)
    private String zip;

    /** ISO 3166-1 alpha-2 country code. */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Data-protection / consent flags (HIPAA BAA, India DPDP consent, DPO contact, …). */
    @Type(JsonBinaryType.class)
    @Column(name = "data_protection", columnDefinition = "jsonb")
    private Map<String, Object> dataProtection;

    /** Country- or tenant-type-specific fields not modelled as columns. */
    @Type(JsonBinaryType.class)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes;
}
