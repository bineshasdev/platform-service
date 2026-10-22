package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuTenantOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuTenantOverrideRepository extends JpaRepository<MenuTenantOverride, UUID> {
    List<MenuTenantOverride> findByTenantId(UUID tenantId);
    List<MenuTenantOverride> findByTenantIdAndMenuItemApplicationId(UUID tenantId, UUID applicationId);
    Optional<MenuTenantOverride> findByTenantIdAndMenuItemId(UUID tenantId, UUID menuItemId);
}
