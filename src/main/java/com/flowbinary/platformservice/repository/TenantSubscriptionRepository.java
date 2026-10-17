package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.SubscriptionStatus;
import com.flowbinary.platformservice.model.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
    Optional<TenantSubscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    List<TenantSubscription> findByTenantId(UUID tenantId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT ts FROM TenantSubscription ts WHERE ts.tenant.id = :tenantId " +
        "AND ts.status IN (com.flowbinary.platformservice.model.SubscriptionStatus.ACTIVE, " +
        "com.flowbinary.platformservice.model.SubscriptionStatus.TRIAL) " +
        "ORDER BY ts.createdAt DESC")
    Optional<TenantSubscription> findActiveByTenantId(UUID tenantId);
}
