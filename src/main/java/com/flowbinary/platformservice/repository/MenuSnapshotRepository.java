package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuSnapshotRepository extends JpaRepository<MenuSnapshot, UUID> {

    @Query("SELECT s FROM MenuSnapshot s WHERE s.application.id = :applicationId AND s.tenant IS NULL ORDER BY s.version DESC")
    List<MenuSnapshot> findGlobal(@Param("applicationId") UUID applicationId);

    @Query("SELECT s FROM MenuSnapshot s WHERE s.application.id = :applicationId AND s.tenant.id = :tenantId ORDER BY s.version DESC")
    List<MenuSnapshot> findForTenant(@Param("applicationId") UUID applicationId, @Param("tenantId") UUID tenantId);
}
