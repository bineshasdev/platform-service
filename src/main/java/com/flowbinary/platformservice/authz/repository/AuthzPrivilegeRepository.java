package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthzPrivilegeRepository extends JpaRepository<AuthzPrivilege, String> {

    java.util.List<AuthzPrivilege> findByOwnerService(String ownerService);
}
