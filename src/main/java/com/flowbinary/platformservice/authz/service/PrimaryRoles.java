package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.repository.RoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Who holds which primary role, for responses and events that still show "the role" of a user. */
@Component
@RequiredArgsConstructor
public class PrimaryRoles {

    private final RoleAssignmentRepository assignments;

    /** Primary role code per user (users without a membership are absent). */
    @Transactional(readOnly = true)
    public Map<UUID, String> codes(UUID tenantId, Collection<UUID> userSubs) {
        if (userSubs.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>();
        for (Object[] row : assignments.primaryRoleCodes(tenantId, userSubs)) out.put((UUID) row[0], (String) row[1]);
        return out;
    }

    @Transactional(readOnly = true)
    public String code(UUID tenantId, UUID userSub) {
        return codes(tenantId, List.of(userSub)).get(userSub);
    }

    /** The tenant's administrators (primary role from the admin template), oldest first. */
    @Transactional(readOnly = true)
    public List<UUID> adminSubs(UUID tenantId) {
        return assignments.adminSubs(tenantId);
    }
}
