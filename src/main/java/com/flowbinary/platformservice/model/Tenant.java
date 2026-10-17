package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * Root tenant entity — represents an organisation/company on the platform.
 * Maps to: tenants
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    /** Keycloak Organisation ID — populated after org creation during signup. */
    @Column(name = "tenant_id", unique = true)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    @Builder.Default
    private TenantPlan plan = TenantPlan.free;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.active;

    /**
     * Business category of this organisation. Nullable for legacy rows created
     * before the typed signup flow; always set by {@code ClinicRegistrationService}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TenantType type;

    /**
     * Verification lifecycle of this tenant's legal + licensing credentials.
     * Signup completes immediately with {@link VerificationStatus#PENDING};
     * the tenant becomes publicly discoverable only once {@link VerificationStatus#VERIFIED}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** True once credentials are verified — gates public directory / search listing. */
    @Column(name = "listed", nullable = false)
    @Builder.Default
    private boolean listed = false;

    /** Set once the admin email is confirmed via OTP; null means still unverified. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Timestamp the tenant's credentials were approved. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "website")
    private String website;

    /** ISO 3166-1 alpha-3 country code, e.g. "IND". */
    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "state")
    private String state;

    @Column(name = "zip", length = 20)
    private String zip;

    /** BCP-47 locale tag, e.g. "en-GB". */
    @Column(name = "locale", length = 10)
    private String locale;

    /** ISO 4217 currency code, e.g. "INR". */
    @Column(name = "currency", length = 3)
    private String currency;

    /** Temporary plain-text password for the admin user set during signup; clear after first login. */
    @Column(name = "admin_temp_password")
    private String adminTempPassword;

    @Column(name = "effective_start_date", nullable = false)
    @Builder.Default
    private Instant effectiveStartDate = Instant.now();

    @Column(name = "effective_end_date")
    private Instant effectiveEndDate;

    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
