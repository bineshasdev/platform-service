package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.TenantUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {
    Optional<TenantUser> findByTenantIdAndAuthUserId(UUID tenantId, UUID authUserId);

    List<TenantUser> findByTenantIdAndAuthUserIdIn(UUID tenantId, java.util.Collection<UUID> authUserIds);
    Optional<TenantUser> findByTenantIdAndEmail(UUID tenantId, String email);
    List<TenantUser> findByTenantId(UUID tenantId);
    List<TenantUser> findByTenantIdAndStatus(UUID tenantId, String status);
    boolean existsByTenantIdAndAuthUserId(UUID tenantId, UUID authUserId);
    @Query("SELECT LOWER(u.username) FROM TenantUser u WHERE u.tenant.id = :tenantId AND LOWER(u.username) LIKE LOWER(:prefix) || '%'")
    Set<String> findLowercaseUsernamesWithPrefix(@Param("tenantId") UUID tenantId, @Param("prefix") String prefix);

    /** Resolves the tenant of a user by matching email or username, case-insensitively. */
    @Query("SELECT u FROM TenantUser u WHERE LOWER(u.email) = LOWER(:identifier) OR LOWER(u.username) = LOWER(:identifier)")
    Optional<TenantUser> findFirstByEmailOrUsernameIgnoreCase(@Param("identifier") String identifier);

    @Query("""
            SELECT u FROM TenantUser u
            WHERE u.tenant.id = :tenantId
              AND (:search IS NULL OR :search = ''
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<TenantUser> searchByTenant(@Param("tenantId") UUID tenantId,
                                    @Param("search") String search,
                                    Pageable pageable);
}
