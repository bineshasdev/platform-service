package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.CredentialStatus;
import com.flowbinary.platformservice.model.TenantCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantCredentialRepository extends JpaRepository<TenantCredential, UUID> {

    List<TenantCredential> findByTenantId(UUID tenantId);

    Optional<TenantCredential> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, CredentialStatus status);

    List<TenantCredential> findByStatusAndExpiryDateBefore(CredentialStatus status, LocalDate date);
}
