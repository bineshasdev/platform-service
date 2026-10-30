package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A role held through a membership: exactly one primary, any number of extras.
 * Extras can be time-bound; any assignment can be limited to locations.
 * Maps to: authz_role_assignments
 */
@Entity
@Table(name = "authz_role_assignments")
@Getter
@Setter
@NoArgsConstructor
public class RoleAssignment extends BaseEntity {

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** Empty = every location of the tenant. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "location_ids", columnDefinition = "uuid[]", nullable = false)
    private List<UUID> locationIds = new ArrayList<>();

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom = Instant.now();

    /** Null = open-ended. */
    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public boolean isActiveAt(Instant now) {
        return !validFrom.isAfter(now) && (validTo == null || validTo.isAfter(now));
    }
}
