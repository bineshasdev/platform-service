package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuGroupAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuGroupAccessRepository extends JpaRepository<MenuGroupAccess, UUID> {

    @Query("SELECT a FROM MenuGroupAccess a WHERE a.group.id IN :groupIds")
    List<MenuGroupAccess> findByGroupIds(@Param("groupIds") Collection<UUID> groupIds);

    /** A tenant's group assignments within one application. */
    @Query("""
            SELECT a FROM MenuGroupAccess a
            JOIN FETCH a.menuItem mi JOIN FETCH a.group g
            WHERE g.tenant.id = :tenantId AND mi.application.id = :applicationId
            """)
    List<MenuGroupAccess> findForTenantAndApplication(@Param("tenantId") UUID tenantId,
                                                      @Param("applicationId") UUID applicationId);

    /** Every group assignment of a tenant in one application, by application code. */
    @Query("""
            SELECT a FROM MenuGroupAccess a
            JOIN FETCH a.menuItem mi JOIN FETCH a.group g
            WHERE g.tenant.id = :tenantId AND mi.application.code = :applicationCode
            """)
    List<MenuGroupAccess> findForTenantAndApplicationCode(@Param("tenantId") UUID tenantId,
                                                          @Param("applicationCode") String applicationCode);

    List<MenuGroupAccess> findByMenuItemIdIn(Collection<UUID> menuItemIds);

    boolean existsByGroupId(UUID groupId);
}
