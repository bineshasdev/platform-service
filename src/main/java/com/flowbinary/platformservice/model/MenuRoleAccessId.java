package com.flowbinary.platformservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MenuRoleAccessId implements Serializable {

    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Column(name = "role_id")
    private UUID roleId;
}
