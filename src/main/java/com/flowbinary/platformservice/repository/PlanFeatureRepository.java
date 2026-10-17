package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.PlanFeature;
import com.flowbinary.platformservice.model.PlanFeatureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, PlanFeatureId> {
    List<PlanFeature> findByIdPlanId(UUID planId);
    List<PlanFeature> findByIdFeatureId(UUID featureId);

    @Query("SELECT pf FROM PlanFeature pf JOIN pf.plan p WHERE p.code = :planCode AND pf.enabled = true")
    List<PlanFeature> findEnabledByPlanCode(com.flowbinary.platformservice.model.TenantPlan planCode);
}
