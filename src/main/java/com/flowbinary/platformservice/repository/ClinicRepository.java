package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.domain.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    Optional<Clinic> findByTenantId(UUID tenantId);
    boolean existsByTenantId(UUID tenantId);
    List<Clinic> findByNameIgnoreCase(String name);
}
