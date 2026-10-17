package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Plan;
import com.flowbinary.platformservice.model.TenantPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCode(TenantPlan code);
    List<Plan> findByIsActiveTrue();
}
