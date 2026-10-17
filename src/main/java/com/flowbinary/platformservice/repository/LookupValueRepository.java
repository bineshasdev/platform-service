package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.LookupValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {
    List<LookupValue> findByCategoryCodeAndIsActiveTrueOrderBySortOrderAsc(String categoryCode);
    List<LookupValue> findByCategoryCodeAndParentCodeOrderBySortOrderAsc(String categoryCode, String parentCode);
    Optional<LookupValue> findByCategoryCodeAndCode(String categoryCode, String code);
    List<LookupValue> findByCategoryId(UUID categoryId);
}
