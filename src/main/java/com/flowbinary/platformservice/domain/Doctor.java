package com.flowbinary.platformservice.domain;

import com.flowbinary.platformservice.model.BaseEntity;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.VerificationStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A platform-wide practitioner identity. Not tenant-scoped — a doctor may work
 * across several clinics and, per the "Both" model, may also own a practice
 * (see {@link #ownedTenant}).
 *
 * <p>Created {@link VerificationStatus#PENDING} / {@code listed = false}; becomes
 * publicly discoverable only after medical-council credentials are verified.
 * Maps to: doctors
 */
@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends BaseEntity {

    /** Keycloak user UUID. Auth lives in Keycloak; this is the link. */
    @Column(name = "auth_user_id", nullable = false, unique = true)
    private UUID authUserId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** E.164 mobile number. */
    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    /** Short professional headline, e.g. "Consultant Cardiologist". */
    @Column(name = "headline")
    private String headline;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    // ── Professional ──────────────────────────────────────────────────────

    @Column(name = "primary_specialty", length = 120)
    private String primarySpecialty;

    /** All specialties, e.g. ["CARDIOLOGY","INTERNAL_MEDICINE"]. */
    @Type(JsonBinaryType.class)
    @Column(name = "specialties", columnDefinition = "jsonb")
    private List<String> specialties;

    /** Degrees, e.g. [{"degree":"MBBS","institute":"...","year":2008}]. */
    @Type(JsonBinaryType.class)
    @Column(name = "qualifications", columnDefinition = "jsonb")
    private List<Map<String, Object>> qualifications;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Type(JsonBinaryType.class)
    @Column(name = "languages", columnDefinition = "jsonb")
    private List<String> languages;

    // ── Medical council registration ──────────────────────────────────────

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    /** Issuing council / board, e.g. "Karnataka Medical Council" or "NMC". */
    @Column(name = "registration_council", length = 160)
    private String registrationCouncil;

    @Column(name = "registration_year")
    private Integer registrationYear;

    @Column(name = "registration_state", length = 120)
    private String registrationState;

    // ── Verification / discovery ──────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** True once verified — gates public search / directory listing. */
    @Column(name = "listed", nullable = false)
    private boolean listed = false;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "state", length = 120)
    private String state;

    @Column(name = "country", length = 2)
    private String country;

    /**
     * Practice owned by this doctor, when they also run their own clinic
     * (tenant type {@code PRIVATE_CLINIC}). Null for doctors who only practise
     * at other clinics.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owned_tenant_id")
    private Tenant ownedTenant;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
