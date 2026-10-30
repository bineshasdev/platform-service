package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A user's relationship with one tenant, or with the platform when
 * {@code tenantId} is null. Roles hang off the membership, so the same person
 * can hold different roles in different tenants.
 * Maps to: authz_memberships
 */
@Entity
@Table(name = "authz_memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends BaseEntity {

    /** Null for a platform (operator) membership. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Keycloak user id: the token's {@code sub}. */
    @Column(name = "user_sub", nullable = false)
    private UUID userSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;
}
