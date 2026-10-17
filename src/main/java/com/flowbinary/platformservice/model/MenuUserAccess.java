package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-user addition to, or revocation of, the menu set their roles grant.
 * {@link MenuAccessEffect#DENY} always wins over any grant.
 * Maps to: menu_user_access
 */
@Entity
@Table(name = "menu_user_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuUserAccess extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private TenantUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private MenuAccessEffect effect;

    /** Why the override exists — surfaced in the access-review report (PR-012). */
    @Column(name = "reason")
    private String reason;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private Instant validFrom = Instant.now();

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public boolean isActiveAt(Instant at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }
}
