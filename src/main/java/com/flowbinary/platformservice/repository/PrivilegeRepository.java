package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, UUID> {
    Optional<Privilege> findByCode(String code);
    List<Privilege> findByCodeIn(Collection<String> codes);
    List<Privilege> findByIsActiveTrueOrderByCategoryAscCodeAsc();
    List<Privilege> findByCategoryAndIsActiveTrue(String category);
}
