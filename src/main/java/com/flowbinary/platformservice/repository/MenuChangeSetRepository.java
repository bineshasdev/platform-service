package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuChangeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuChangeSetRepository extends JpaRepository<MenuChangeSet, UUID> {

    @Query("""
            SELECT c FROM MenuChangeSet c
            WHERE c.application.id = :applicationId AND c.tenant IS NULL
              AND c.status = com.flowbinary.platformservice.model.MenuChangeSetStatus.OPEN
            """)
    Optional<MenuChangeSet> findOpenGlobal(@Param("applicationId") UUID applicationId);

    @Query("""
            SELECT c FROM MenuChangeSet c
            WHERE c.application.id = :applicationId AND c.tenant.id = :tenantId
              AND c.status = com.flowbinary.platformservice.model.MenuChangeSetStatus.OPEN
            """)
    Optional<MenuChangeSet> findOpenForTenant(@Param("applicationId") UUID applicationId,
                                              @Param("tenantId") UUID tenantId);

    default Optional<MenuChangeSet> findOpenGlobalOrTenant(com.flowbinary.platformservice.service.MenuScope scope) {
        return scope.isGlobal()
                ? findOpenGlobal(scope.application().getId())
                : findOpenForTenant(scope.application().getId(), scope.tenantId());
    }
}
