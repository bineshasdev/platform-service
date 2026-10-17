package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantPlan;
import com.flowbinary.platformservice.model.TenantStatus;
import com.flowbinary.platformservice.model.TenantType;
import com.flowbinary.platformservice.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByTenantId(String keycloakOrgId);
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByEmail(String email);
    boolean existsBySlug(String slug);
    boolean existsByEmail(String email);
    List<Tenant> findByPlan(TenantPlan plan);
    List<Tenant> findByStatus(TenantStatus status);
    List<Tenant> findByType(TenantType type);
    List<Tenant> findByVerificationStatus(VerificationStatus verificationStatus);

    /** Publicly discoverable tenants of a given type — feeds the search / directory index. */
    List<Tenant> findByTypeAndListedTrue(TenantType type);
}
