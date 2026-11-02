package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.Membership;
import com.flowbinary.platformservice.authz.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByTenantIdAndUserSub(UUID tenantId, UUID userSub);

    @Query("SELECT m FROM Membership m WHERE m.tenantId IS NULL AND m.userSub = :userSub")
    Optional<Membership> findPlatformMembership(UUID userSub);

    List<Membership> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    List<Membership> findByTenantIdIsNullOrderByCreatedAtAsc();

    List<Membership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);
}
