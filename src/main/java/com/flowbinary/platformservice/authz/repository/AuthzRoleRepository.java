package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.AuthzRole;
import com.flowbinary.platformservice.authz.model.RoleKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthzRoleRepository extends JpaRepository<AuthzRole, UUID> {

    List<AuthzRole> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<AuthzRole> findByIdIn(Collection<UUID> ids);

    Optional<AuthzRole> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<AuthzRole> findByTenantIdAndTemplateIdAndKind(UUID tenantId, UUID templateId, RoleKind kind);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
