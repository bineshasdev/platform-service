package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuRoleAccess;
import com.flowbinary.platformservice.model.MenuRoleAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuRoleAccessRepository extends JpaRepository<MenuRoleAccess, MenuRoleAccessId> {
    List<MenuRoleAccess> findByIdMenuItemId(UUID menuItemId);
    List<MenuRoleAccess> findByIdRoleId(UUID roleId);
    List<MenuRoleAccess> findByIdRoleIdIn(Collection<UUID> roleIds);
    void deleteByIdRoleId(UUID roleId);
}
