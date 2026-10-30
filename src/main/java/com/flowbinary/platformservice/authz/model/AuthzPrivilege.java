package com.flowbinary.platformservice.authz.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * One protected action, named {@code <service>.<resource>.<action>} and owned by
 * the service that enforces it. The unit of every authorization check.
 * Maps to: authz_privileges
 */
@Entity
@Table(name = "authz_privileges")
@Getter
@Setter
@NoArgsConstructor
public class AuthzPrivilege {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "owner_service", nullable = false)
    private String ownerService;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** Tenant types the privilege exists in; empty for platform-only privileges. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tenant_types", columnDefinition = "text[]", nullable = false)
    private List<String> tenantTypes = List.of();

    /** Plan feature the tenant must have enabled; null = every plan. */
    @Column(name = "feature_code")
    private String featureCode;

    @Column(name = "scope_kind", nullable = false)
    private String scopeKind;

    @Column(name = "phi", nullable = false)
    private boolean phi;

    @Column(name = "clinical_write", nullable = false)
    private boolean clinicalWrite;

    @Column(name = "assignable_by_tenant", nullable = false)
    private boolean assignableByTenant;

    @Column(name = "risk", nullable = false)
    private String risk;

    @Column(name = "status", nullable = false)
    private String status;

    /** The old {@code privileges.code} this replaces; used while both models run. */
    @Column(name = "legacy_code")
    private String legacyCode;

    public boolean isActive() {
        return !"DEPRECATED".equals(status);
    }

    /** True when a role of a tenant of {@code tenantType} may hold this privilege. */
    public boolean usableIn(String tenantType) {
        return assignableByTenant && tenantTypes != null && tenantTypes.contains(tenantType);
    }
}
