package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuModuleManifest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuModuleManifestRepository extends JpaRepository<MenuModuleManifest, UUID> {
    List<MenuModuleManifest> findByModuleIdOrderByReceivedAtDesc(UUID moduleId, Pageable page);
}
