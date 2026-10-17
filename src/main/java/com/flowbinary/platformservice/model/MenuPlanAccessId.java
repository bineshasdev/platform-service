package com.flowbinary.platformservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MenuPlanAccessId implements Serializable {

    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan")
    private TenantPlan plan;
}
