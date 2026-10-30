package com.flowbinary.platformservice.authz.model;

import com.flowbinary.platformservice.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A platform-defined role for one tenant type ({@code CLINIC → doctor}). Tenant
 * roles of kind TEMPLATE and DERIVED point here, so a privilege added to the
 * template reaches them without touching tenant data.
 * Maps to: authz_role_templates (+ authz_role_template_privileges)
 */
@Entity
@Table(name = "authz_role_templates")
@Getter
@Setter
@NoArgsConstructor
public class RoleTemplate extends BaseEntity {

    @Column(name = "tenant_type", nullable = false)
    private String tenantType;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "primary_eligible", nullable = false)
    private boolean primaryEligible = true;

    /** The template the tenant's creator receives; one per tenant type. */
    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    /** The least-privileged template of the type; one per tenant type. */
    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authz_role_template_privileges", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "privilege_code")
    private Set<String> privileges = new HashSet<>();
}
