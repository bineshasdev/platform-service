package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.TenantStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantStorageConfigRepository extends JpaRepository<TenantStorageConfig, UUID> {

    Optional<TenantStorageConfig> findByTenantId(UUID tenantId);

    /** Atomically increment used_bytes after a successful upload. */
    @Modifying
    @Query("UPDATE TenantStorageConfig c SET c.usedBytes = c.usedBytes + :bytes WHERE c.tenant.id = :tenantId")
    void incrementUsedBytes(@Param("tenantId") UUID tenantId, @Param("bytes") long bytes);

    /** Atomically decrement used_bytes after a successful delete (floors at 0). */
    @Modifying
    @Query("UPDATE TenantStorageConfig c SET c.usedBytes = GREATEST(0, c.usedBytes - :bytes) WHERE c.tenant.id = :tenantId")
    void decrementUsedBytes(@Param("tenantId") UUID tenantId, @Param("bytes") long bytes);
}
