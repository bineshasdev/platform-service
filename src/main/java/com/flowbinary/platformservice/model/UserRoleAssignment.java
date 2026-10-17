package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Assigns a {@link Role} to a {@link TenantUser}. A user may hold several roles
 * (a doctor who also runs the clinic); exactly one is flagged primary, and
 * {@code users.role} is kept as a denormalised cache of it.
 *
 * <p>Named {@code UserRoleAssignment} rather than {@code UserRole} because that
 * name is taken by the role-code enum.
 * Maps to: user_roles
 */
@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private TenantUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Optional location scope — PR-003 scopes access to a tenant and, optionally, a location. */
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private Instant validFrom = Instant.now();

    /** Null = open-ended. Set for time-bounded assignments such as locums or delegation. */
    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    /** True when this assignment is in force at {@code at}. */
    public boolean isActiveAt(Instant at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }
}
