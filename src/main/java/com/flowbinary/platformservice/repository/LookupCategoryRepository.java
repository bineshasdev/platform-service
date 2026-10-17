package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.LookupCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LookupCategoryRepository extends JpaRepository<LookupCategory, UUID> {
    Optional<LookupCategory> findByCode(String code);
    List<LookupCategory> findByIsSystemTrue();
    boolean existsByCode(String code);
}
