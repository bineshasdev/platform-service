package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    Optional<UserGroup> findByTenantIdAndApplicationIdAndCode(UUID tenantId, UUID applicationId, String code);
    List<UserGroup> findByTenantIdAndApplicationIdOrderByNameAsc(UUID tenantId, UUID applicationId);
}
