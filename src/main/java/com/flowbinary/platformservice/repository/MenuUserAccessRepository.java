package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuUserAccessRepository extends JpaRepository<MenuUserAccess, UUID> {
    List<MenuUserAccess> findByUserId(UUID userId);
    List<MenuUserAccess> findByMenuItemIdIn(java.util.Collection<UUID> menuItemIds);
    Optional<MenuUserAccess> findByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    /** A tenant's user-level assignments within one application. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT a FROM MenuUserAccess a
            JOIN FETCH a.menuItem mi JOIN FETCH a.user u
            WHERE u.tenant.id = :tenantId AND mi.application.id = :applicationId
            """)
    List<MenuUserAccess> findForTenantAndApplication(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("applicationId") UUID applicationId);

    void deleteByUserIdAndMenuItemId(UUID userId, UUID menuItemId);
}
