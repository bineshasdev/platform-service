package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The default menu set for a role. A user inherits every item granted to any
 * role they hold, before per-user overrides are applied.
 * Maps to: menu_role_access (composite PK)
 */
@Entity
@Table(name = "menu_role_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuRoleAccess {

    @EmbeddedId
    private MenuRoleAccessId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuItemId")
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
