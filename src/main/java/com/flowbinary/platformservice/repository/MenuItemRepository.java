package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    Optional<MenuItem> findByApplicationCodeAndCode(String applicationCode, String code);

    /**
     * Every live item a tenant can see in an application: the global tree plus
     * the tenant's own items — never another tenant's. Role, plan and privilege
     * gating is applied in memory by {@code MenuResolutionService}, which needs
     * the whole tree to decide whether a parent still has visible children.
     */
    @Query("""
            SELECT m FROM MenuItem m
            LEFT JOIN FETCH m.requiredPrivilege
            WHERE m.application.code = :applicationCode
              AND (m.tenant IS NULL OR m.tenant.id = :tenantId)
              AND m.isActive = true
              AND m.deletedAt IS NULL
            ORDER BY m.sortOrder ASC
            """)
    List<MenuItem> findLiveForTenant(@Param("applicationCode") String applicationCode,
                                     @Param("tenantId") UUID tenantId);

    /**
     * Everything a scope can address — global items plus (for a tenant scope)
     * that tenant's own — including soft-deleted rows, which still hold their codes.
     */
    @Query("""
            SELECT m FROM MenuItem m
            LEFT JOIN FETCH m.requiredPrivilege
            WHERE m.application.id = :applicationId
              AND (m.tenant IS NULL OR m.tenant.id = :tenantId)
            ORDER BY m.depth ASC, m.sortOrder ASC
            """)
    List<MenuItem> findScopeItems(@Param("applicationId") UUID applicationId,
                                  @Param("tenantId") UUID tenantId);

    /** Global scope: only the platform's own items. */
    @Query("""
            SELECT m FROM MenuItem m
            LEFT JOIN FETCH m.requiredPrivilege
            WHERE m.application.id = :applicationId AND m.tenant IS NULL
            ORDER BY m.depth ASC, m.sortOrder ASC
            """)
    List<MenuItem> findGlobalItems(@Param("applicationId") UUID applicationId);

    @Query("""
            SELECT COUNT(m) > 0 FROM MenuItem m
            WHERE m.application.id = :applicationId AND m.tenant IS NOT NULL AND m.code = :code
            """)
    boolean existsTenantOwnedCode(@Param("applicationId") UUID applicationId, @Param("code") String code);

    @Query("""
            SELECT COUNT(m) > 0 FROM MenuItem m
            WHERE m.parent.id IN :parentIds AND m.tenant IS NOT NULL AND m.deletedAt IS NULL
            """)
    boolean existsTenantOwnedChild(@Param("parentIds") Collection<UUID> parentIds);
}
