package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.RoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleTemplateRepository extends JpaRepository<RoleTemplate, UUID> {

    List<RoleTemplate> findByTenantTypeOrderBySortOrderAsc(String tenantType);

    Optional<RoleTemplate> findByTenantTypeAndCode(String tenantType, String code);

    Optional<RoleTemplate> findByTenantTypeAndAdminTrue(String tenantType);

    Optional<RoleTemplate> findByTenantTypeAndFallbackTrue(String tenantType);
}
