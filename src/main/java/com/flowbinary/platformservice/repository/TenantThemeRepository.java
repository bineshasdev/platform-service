package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.TenantTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantThemeRepository extends JpaRepository<TenantTheme, UUID> {
    Optional<TenantTheme> findByTenantId(UUID tenantId);
}
