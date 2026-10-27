package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.UserGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserGroupMemberRepository extends JpaRepository<UserGroupMember, UUID> {

    /** Active groups of an application that a user belongs to. */
    @Query("""
            SELECT m.group.id FROM UserGroupMember m
            WHERE m.user.id = :userId
              AND m.group.isActive = true
              AND m.group.application.code = :applicationCode
            """)
    Set<UUID> findActiveGroupIds(@Param("userId") UUID userId, @Param("applicationCode") String applicationCode);

    @Query("SELECT m FROM UserGroupMember m JOIN FETCH m.user WHERE m.group.id = :groupId")
    List<UserGroupMember> findByGroupIdWithUser(@Param("groupId") UUID groupId);

    Optional<UserGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupId(UUID groupId);
}
