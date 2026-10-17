package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Composes a role from the privilege catalogue.
 * Maps to: role_privileges (composite PK)
 */
@Entity
@Table(name = "role_privileges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePrivilege {

    @EmbeddedId
    private RolePrivilegeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("privilegeId")
    @JoinColumn(name = "privilege_id")
    private Privilege privilege;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private UUID grantedBy;
}
