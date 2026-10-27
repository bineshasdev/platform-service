package com.flowbinary.platformservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Maps to: user_group_members */
@Entity
@Table(name = "user_group_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroupMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private TenantUser user;

    @Column(name = "added_by")
    private UUID addedBy;
}
