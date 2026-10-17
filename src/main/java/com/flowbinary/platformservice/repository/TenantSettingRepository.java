package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.TenantSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSettingRepository extends JpaRepository<TenantSetting, UUID> {
    @Query("SELECT ts FROM TenantSetting ts WHERE ts.tenant.id = :tenantId AND ts.definition.key = :key")
    Optional<TenantSetting> findByTenantIdAndKey(UUID tenantId, String key);

    @Query("SELECT ts FROM TenantSetting ts JOIN FETCH ts.definition WHERE ts.tenant.id = :tenantId")
    List<TenantSetting> findAllByTenantId(UUID tenantId);
}
