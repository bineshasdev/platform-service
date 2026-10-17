package com.flowbinary.platformservice.domain;

import com.flowbinary.platformservice.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A platform-global patient account. One login, usable across any clinic on the
 * platform. Not tenant-scoped and not subject to credential verification.
 * Maps to: patients
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {

    /** Keycloak user UUID. */
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

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

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

    @Column(name = "country", length = 2)
    private String country;

    /** {"name":"...","relation":"...","phone":"..."} */
    @Type(JsonBinaryType.class)
    @Column(name = "emergency_contact", columnDefinition = "jsonb")
    private Map<String, Object> emergencyContact;

    @Column(name = "preferred_language", length = 40)
    private String preferredLanguage;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn = false;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
