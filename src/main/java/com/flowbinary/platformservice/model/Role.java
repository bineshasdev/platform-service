package com.flowbinary.platformservice.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A role a user can hold. Rows with a null {@code tenant} are the platform's
 * system roles, seeded from {@link UserRole}; rows with a tenant are custom
 * roles composed by a clinic admin from the privilege catalogue (PR-004).
 * Maps to: roles
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    /** Null for system roles; set for tenant-owned custom roles. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** Unique within the tenant, or globally for system roles. */
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Optional inheritance — this role also holds its parent's privileges. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id")
    private Role parentRole;

    /** The Keycloak realm role this maps to, for token and authority sync. */
    @Column(name = "platform_role")
    private String platformRole;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "is_assignable", nullable = false)
    @Builder.Default
    private Boolean isAssignable = true;

    /** PR-006 — MFA is mandatory for any role with clinical write access. */
    @Column(name = "requires_mfa", nullable = false)
    @Builder.Default
    private Boolean requiresMfa = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RolePrivilege> privileges = new ArrayList<>();
}
