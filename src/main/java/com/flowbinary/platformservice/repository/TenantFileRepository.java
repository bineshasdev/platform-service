package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.FileType;
import com.flowbinary.platformservice.model.TenantFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantFileRepository extends JpaRepository<TenantFile, UUID> {

    Page<TenantFile> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<TenantFile> findByTenantIdAndFileTypeAndStatus(UUID tenantId, FileType fileType, String status, Pageable pageable);

    Optional<TenantFile> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Returns the most recent active file of a given type for a tenant (e.g. current logo). */
    Optional<TenantFile> findFirstByTenantIdAndFileTypeAndStatusOrderByCreatedAtDesc(
            UUID tenantId, FileType fileType, String status);

    List<TenantFile> findByTenantIdAndStatus(UUID tenantId, String status);
}
