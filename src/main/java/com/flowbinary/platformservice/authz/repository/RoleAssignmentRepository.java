package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByMembershipId(UUID membershipId);

    List<RoleAssignment> findByMembershipIdIn(Collection<UUID> membershipIds);

    boolean existsByRoleId(UUID roleId);

    long countByRoleId(UUID roleId);
}
