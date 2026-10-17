package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Controls which plans can see a given menu item.
 * Maps to: menu_plan_access (composite PK)
 */
@Entity
@Table(name = "menu_plan_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuPlanAccess {

    @EmbeddedId
    private MenuPlanAccessId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("menuItemId")
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;
}
