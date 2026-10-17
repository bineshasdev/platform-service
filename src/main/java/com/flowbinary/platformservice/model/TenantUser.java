package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A user scoped to a tenant. {@code authUserId} holds the Keycloak user UUID.
 * Maps to: users
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_auth_user", columnNames = {"tenant_id", "auth_user_id"}),
                @UniqueConstraint(name = "uq_tenant_email",     columnNames = {"tenant_id", "email"}),
                @UniqueConstraint(name = "uq_tenant_username",  columnNames = {"tenant_id", "username"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Keycloak user UUID. Not a JPA FK — auth lives in Keycloak. */
    @Column(name = "auth_user_id", nullable = false)
    private UUID authUserId;

    @Convert(converter = PlatformRoleConverter.class)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private PlatformRole role = PlatformRole.USER;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "mobile")
    private String mobile;

    /** ISO 3166-1 alpha-3 country code. */
    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "state")
    private String state;

    @Column(name = "pincode", length = 20)
    private String pincode;

    /**
     * FK to the most recent active profile image stored via FileStorageService.
     * Nullable — users without a profile image have no row here.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id")
    private TenantFile profileImage;

    /**
     * Resolved URL for the profile image — either a public CDN/S3 URL or the
     * in-app download path {@code /api/v1/files/{id}/download}.
     * Cached here to avoid a join on every user list query.
     */
    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "start_date_effective")
    @Builder.Default
    private Instant startDateEffective = Instant.now();

    @Column(name = "end_date_effective")
    private Instant endDateEffective;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
