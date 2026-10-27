package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Grants or denies a menu item to every member of a group. A DENY beats any
 * grant — user, group or role. Maps to: menu_group_access
 */
@Entity
@Table(name = "menu_group_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuGroupAccess extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private MenuAccessEffect effect;

    @Column(name = "reason")
    private String reason;

    @Column(name = "assigned_by")
    private UUID assignedBy;
}
