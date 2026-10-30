package com.flowbinary.platformservice.authz.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What one user may do in one tenant (or on the platform when {@code tenantId}
 * is null), as of {@code computedAt}. When {@code status} is not
 * {@link Status#ACTIVE} the privilege set is empty and {@code status} says why.
 */
public record PermissionSet(UUID tenantId,
                            UUID userSub,
                            UUID membershipId,
                            Status status,
                            Set<String> privileges,
                            String primaryRole,
                            List<String> roles,
                            Instant computedAt) {

    public enum Status {
        ACTIVE,
        NO_MEMBERSHIP,
        MEMBERSHIP_INACTIVE,
        TENANT_INACTIVE
    }

    public static PermissionSet denied(UUID tenantId, UUID userSub, UUID membershipId, Status status, Instant now) {
        return new PermissionSet(tenantId, userSub, membershipId, status, Set.of(), null, List.of(), now);
    }

    public boolean has(String privilege) {
        return status == Status.ACTIVE && privileges.contains(privilege);
    }
}
