package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuUserAccessRepository extends JpaRepository<MenuUserAccess, UUID> {
    List<MenuUserAccess> findByUserId(UUID userId);
    Optional<MenuUserAccess> findByUserIdAndMenuItemId(UUID userId, UUID menuItemId);
    void deleteByUserIdAndMenuItemId(UUID userId, UUID menuItemId);
}
