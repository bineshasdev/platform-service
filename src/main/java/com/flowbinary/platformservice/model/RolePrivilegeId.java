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
public class RolePrivilegeId implements Serializable {

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "privilege_id")
    private UUID privilegeId;
}
