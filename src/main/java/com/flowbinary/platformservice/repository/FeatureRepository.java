package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Feature;
import com.flowbinary.platformservice.model.FeatureCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByCode(String code);
    List<Feature> findByCategory(FeatureCategory category);
    boolean existsByCode(String code);
}
