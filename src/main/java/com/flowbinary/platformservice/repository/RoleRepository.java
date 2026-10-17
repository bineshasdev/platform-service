package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** A platform system role, by code. */
    Optional<Role> findByTenantIsNullAndCode(String code);

    /** A tenant's own custom role, by code. */
    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    List<Role> findByTenantIsNullAndIsActiveTrueOrderBySortOrderAsc();

    /** Every role a tenant can assign: the platform system roles plus its own. */
    @Query("""
            SELECT r FROM Role r
            WHERE r.isActive = true
              AND r.isAssignable = true
              AND (r.tenant IS NULL OR r.tenant.id = :tenantId)
            ORDER BY r.sortOrder ASC, r.name ASC
            """)
    List<Role> findAssignableForTenant(@Param("tenantId") UUID tenantId);

    /** First system role mapped to a Keycloak realm role — the enum-era fallback. */
    Optional<Role> findFirstByTenantIsNullAndPlatformRoleOrderBySortOrderAsc(String platformRole);
}
