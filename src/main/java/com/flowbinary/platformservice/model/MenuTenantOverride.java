package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A tenant's presentation override of a platform-owned master menu item.
 * Null fields inherit the master value; tenants cannot create menu items.
 * Maps to: menu_tenant_overrides
 */
@Entity
@Table(name = "menu_tenant_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuTenantOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    /** Null = inherit the master label. */
    @Column(name = "label")
    private String label;

    @Column(name = "icon")
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private Boolean isHidden = false;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
