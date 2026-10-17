package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    /** Assignments in force at {@code at}, with the role eagerly loaded. */
    @Query("""
            SELECT ura FROM UserRoleAssignment ura
            JOIN FETCH ura.role r
            WHERE ura.user.id = :userId
              AND r.isActive = true
              AND ura.validFrom <= :at
              AND (ura.validTo IS NULL OR ura.validTo > :at)
            ORDER BY r.sortOrder ASC
            """)
    List<UserRoleAssignment> findActiveByUserId(@Param("userId") UUID userId, @Param("at") Instant at);

    Optional<UserRoleAssignment> findByUserIdAndIsPrimaryTrue(UUID userId);

    List<UserRoleAssignment> findByUserId(UUID userId);

    List<UserRoleAssignment> findByRoleId(UUID roleId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);
}
