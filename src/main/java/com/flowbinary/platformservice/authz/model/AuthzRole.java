package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A role that can be assigned. Tenant roles are a template plus deltas
 * (TEMPLATE, DERIVED) or a set of GRANTs with no template (CUSTOM); platform
 * roles (tenant null) hold platform-only privileges as GRANTs.
 * Maps to: authz_roles (+ authz_role_privilege_deltas)
 */
@Entity
@Table(name = "authz_roles")
@Getter
@Setter
@NoArgsConstructor
public class AuthzRole extends BaseEntity {

    /** Null for platform roles. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Set for TEMPLATE and DERIVED roles. */
    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private RoleKind kind;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Privilege code → GRANT or DENY, applied on top of the template. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authz_role_privilege_deltas", joinColumns = @JoinColumn(name = "role_id"))
    @MapKeyColumn(name = "privilege_code")
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private Map<String, DeltaEffect> deltas = new HashMap<>();

    public boolean isPrimaryEligible() {
        return kind != RoleKind.CUSTOM;
    }
}
