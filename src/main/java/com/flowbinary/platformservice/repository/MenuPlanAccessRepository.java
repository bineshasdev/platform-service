package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuPlanAccess;
import com.flowbinary.platformservice.model.MenuPlanAccessId;
import com.flowbinary.platformservice.model.TenantPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuPlanAccessRepository extends JpaRepository<MenuPlanAccess, MenuPlanAccessId> {
    List<MenuPlanAccess> findByIdMenuItemId(UUID menuItemId);
    List<MenuPlanAccess> findByIdPlan(TenantPlan plan);
}
