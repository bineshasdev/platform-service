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
     * Scheduled and active temporary menus are returned whatever their window (so the
     * compiled catalogue stays valid across a start or end); {@code MenuVisibilityPolicy}
     * checks the window against the current time on every request.
     */
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.application.code = :applicationCode
              AND (m.tenant IS NULL OR m.tenant.id = :tenantId)
              AND m.isActive = true
              AND m.deletedAt IS NULL
              AND ((m.source <> com.flowbinary.platformservice.model.MenuSource.TEMPORARY
                    AND m.lifecycleStatus = com.flowbinary.platformservice.model.MenuLifecycle.ACTIVE)
                   OR (m.source = com.flowbinary.platformservice.model.MenuSource.TEMPORARY
                       AND m.lifecycleStatus IN (com.flowbinary.platformservice.model.MenuLifecycle.SCHEDULED,
                                             com.flowbinary.platformservice.model.MenuLifecycle.ACTIVE)
                       AND m.endsAt > :now))
            ORDER BY m.sortOrder ASC
            """)
    List<MenuItem> findCatalogueSource(@Param("applicationCode") String applicationCode,
                                     @Param("tenantId") UUID tenantId,
                                     @Param("now") java.time.Instant now);

    /**
     * Everything a scope can address — global items plus (for a tenant scope)
     * that tenant's own — including soft-deleted rows, which still hold their codes.
     */
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.application.id = :applicationId
              AND (m.tenant IS NULL OR m.tenant.id = :tenantId)
            ORDER BY m.depth ASC, m.sortOrder ASC
            """)
    List<MenuItem> findScopeItems(@Param("applicationId") UUID applicationId,
                                  @Param("tenantId") UUID tenantId);

    /** Global scope: only the platform's own items. */
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.application.id = :applicationId AND m.tenant IS NULL
            ORDER BY m.depth ASC, m.sortOrder ASC
            """)
    List<MenuItem> findGlobalItems(@Param("applicationId") UUID applicationId);

    @Query("""
            SELECT COUNT(m) > 0 FROM MenuItem m
            WHERE m.application.id = :applicationId AND m.tenant IS NOT NULL AND m.code = :code
            """)
    boolean existsTenantOwnedCode(@Param("applicationId") UUID applicationId, @Param("code") String code);

    /** True when a non-module item, live or deleted, is {@code code} or sits in its namespace ({@code code.*}). */
    @Query("""
            SELECT COUNT(m) > 0 FROM MenuItem m
            WHERE m.application.id = :applicationId AND m.module IS NULL
              AND (m.code = :code OR SUBSTRING(m.code, 1, LENGTH(:prefix)) = :prefix)
            """)
    boolean existsNonModuleInNamespace(@Param("applicationId") UUID applicationId,
                                       @Param("code") String code, @Param("prefix") String prefix);

    @Query("""
            SELECT COUNT(m) > 0 FROM MenuItem m
            WHERE m.parent.id IN :parentIds AND m.tenant IS NOT NULL AND m.deletedAt IS NULL
            """)
    boolean existsTenantOwnedChild(@Param("parentIds") Collection<UUID> parentIds);

    /** Scheduled temporary menus whose window has opened. Rows another instance holds are skipped. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))   // SKIP LOCKED
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.source = com.flowbinary.platformservice.model.MenuSource.TEMPORARY
              AND m.lifecycleStatus = com.flowbinary.platformservice.model.MenuLifecycle.SCHEDULED
              AND m.startsAt <= :now AND m.endsAt > :now
            ORDER BY m.startsAt
            """)
    List<MenuItem> lockDueToActivate(@Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);

    /** Temporary menus whose window has closed but whose status has not caught up. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))   // SKIP LOCKED
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.source = com.flowbinary.platformservice.model.MenuSource.TEMPORARY
              AND m.lifecycleStatus IN (com.flowbinary.platformservice.model.MenuLifecycle.SCHEDULED,
                                        com.flowbinary.platformservice.model.MenuLifecycle.ACTIVE)
              AND m.endsAt <= :now
            ORDER BY m.endsAt
            """)
    List<MenuItem> lockDueToExpire(@Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);
}
