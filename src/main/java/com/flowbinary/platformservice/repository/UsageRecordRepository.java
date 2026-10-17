package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    Optional<UsageRecord> findByTenantIdAndFeatureIdAndPeriod(UUID tenantId, UUID featureId, String period);
    List<UsageRecord> findByTenantIdAndPeriod(UUID tenantId, String period);
    List<UsageRecord> findByTenantId(UUID tenantId);
}
