package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    Optional<MenuItem> findByApplicationCodeAndCode(String applicationCode, String code);

    /**
     * Every live item in an application's tree — active, not soft-deleted.
     * Role, plan and privilege gating is applied in memory by
     * {@code MenuResolutionService}, which needs the whole tree to decide
     * whether a parent group still has visible children.
     */
    @Query("""
            SELECT m FROM MenuItem m
            LEFT JOIN FETCH m.requiredPrivilege
            WHERE m.application.code = :applicationCode
              AND m.isActive = true
              AND m.deletedAt IS NULL
            ORDER BY m.sortOrder ASC
            """)
    List<MenuItem> findLiveByApplicationCode(@Param("applicationCode") String applicationCode);

    /** Master catalogue for an application, including hidden and inactive items. */
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.application.code = :applicationCode
              AND m.deletedAt IS NULL
            ORDER BY m.depth ASC, m.sortOrder ASC
            """)
    List<MenuItem> findCatalogueByApplicationCode(@Param("applicationCode") String applicationCode);

    /** Direct children of an item, for incremental tree loads. */
    List<MenuItem> findByParentIdAndIsActiveTrueOrderBySortOrderAsc(UUID parentId);
}
