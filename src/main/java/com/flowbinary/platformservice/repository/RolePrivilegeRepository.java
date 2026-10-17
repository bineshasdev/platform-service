package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.RolePrivilege;
import com.flowbinary.platformservice.model.RolePrivilegeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RolePrivilegeRepository extends JpaRepository<RolePrivilege, RolePrivilegeId> {

    List<RolePrivilege> findByIdRoleId(UUID roleId);

    /**
     * Privilege codes held by any of the given roles. Does not walk
     * {@code parent_role_id}; callers that support inheritance expand the
     * role set first.
     */
    @Query("""
            SELECT DISTINCT rp.privilege.code
            FROM RolePrivilege rp
            WHERE rp.role.id IN :roleIds
              AND rp.privilege.isActive = true
            """)
    Set<String> findPrivilegeCodesByRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    void deleteByIdRoleId(UUID roleId);
}
