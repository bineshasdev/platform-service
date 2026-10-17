package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.TenantLegalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantLegalProfileRepository extends JpaRepository<TenantLegalProfile, UUID> {

    Optional<TenantLegalProfile> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
