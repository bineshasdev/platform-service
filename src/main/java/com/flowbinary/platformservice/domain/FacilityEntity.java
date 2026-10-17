package com.flowbinary.platformservice.domain;

import com.flowbinary.platformservice.model.BaseEntity;
import com.flowbinary.platformservice.model.Tenant;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Common shape shared by every physical facility type ({@link Clinic},
 * {@link Lab}, {@link Store}).
 *
 * <p>Each concrete facility is 1:1 with a {@link Tenant} — the tenant is the
 * account / org, the facility is the operational unit patients interact with.
 * Carries id + audit columns via {@link BaseEntity}.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class FacilityEntity extends BaseEntity {

    /** Owning tenant. One facility row per tenant. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    /** Public-facing name of the facility. */
    @Column(name = "name", nullable = false)
    private String name;

    /** Registered legal name, when different from {@link #name}. */
    @Column(name = "legal_name")
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FacilityStatus status = FacilityStatus.ACTIVE;

    // ── Contact ───────────────────────────────────────────────────────────

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "alternate_phone", length = 30)
    private String alternatePhone;

    @Column(name = "website")
    private String website;

    // ── Address ───────────────────────────────────────────────────────────

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

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    /** IANA timezone, e.g. "Asia/Kolkata". */
    @Column(name = "timezone", length = 40)
    private String timezone;

    // ── Descriptive ───────────────────────────────────────────────────────

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Weekly opening hours, e.g. {@code {"mon":[["09:00","13:00"],["17:00","20:00"]], ...}}.
     * Empty until set — e.g. clinic self-signup collects this later during enrollment,
     * not at registration. Defaulted here (not left null) because the column is
     * {@code NOT NULL DEFAULT '{}'}; an explicit Java {@code null} would make Hibernate
     * send SQL {@code NULL} in the INSERT, which overrides the column default and trips
     * the NOT NULL constraint — only an omitted column lets Postgres apply its default.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "operating_hours", columnDefinition = "jsonb")
    private Map<String, Object> operatingHours = new HashMap<>();

    /** Free-form facility amenities / tags. Defaulted empty for the same reason as {@link #operatingHours}. */
    @Type(JsonBinaryType.class)
    @Column(name = "amenities", columnDefinition = "jsonb")
    private Map<String, Object> amenities = new HashMap<>();

    /** Facility-scoped configuration overrides. Defaulted empty for the same reason as {@link #operatingHours}. */
    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings = new HashMap<>();

    /** Arbitrary extension data. Defaulted empty for the same reason as {@link #operatingHours}. */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
}
