package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByCode(String code);
    List<Application> findByIsActiveTrueOrderBySortOrderAsc();
}
