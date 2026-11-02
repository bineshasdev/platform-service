package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByMembershipId(UUID membershipId);

    List<RoleAssignment> findByMembershipIdIn(Collection<UUID> membershipIds);

    /** (user, primary role code) for the given users of a tenant. */
    @Query("""
            SELECT m.userSub, r.code FROM Membership m, RoleAssignment a, AuthzRole r
            WHERE m.tenantId = :tenantId AND m.userSub IN :subs
              AND a.membershipId = m.id AND a.primary = true AND r.id = a.roleId
            """)
    List<Object[]> primaryRoleCodes(@Param("tenantId") UUID tenantId, @Param("subs") Collection<UUID> subs);

    /** Users of a tenant whose primary role comes from the type's admin template, oldest first. */
    @Query("""
            SELECT m.userSub FROM Membership m, RoleAssignment a, AuthzRole r, RoleTemplate t
            WHERE m.tenantId = :tenantId AND m.status = com.flowbinary.platformservice.authz.model.MembershipStatus.ACTIVE
              AND a.membershipId = m.id AND a.primary = true AND r.id = a.roleId
              AND t.id = r.templateId AND t.admin = true
            ORDER BY m.createdAt ASC
            """)
    List<UUID> adminSubs(@Param("tenantId") UUID tenantId);

    boolean existsByRoleId(UUID roleId);

    long countByRoleId(UUID roleId);
}
