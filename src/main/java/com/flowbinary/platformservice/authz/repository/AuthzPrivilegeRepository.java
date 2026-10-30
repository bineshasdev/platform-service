package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthzPrivilegeRepository extends JpaRepository<AuthzPrivilege, String> {
}
