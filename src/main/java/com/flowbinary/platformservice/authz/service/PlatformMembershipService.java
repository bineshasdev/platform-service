package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.authz.model.*;
import com.flowbinary.platformservice.authz.repository.AuthzRoleRepository;
import com.flowbinary.platformservice.authz.repository.MembershipRepository;
import com.flowbinary.platformservice.authz.repository.RoleAssignmentRepository;
import com.flowbinary.platformservice.exception.AuthzRuleException;
import com.flowbinary.platformservice.exception.AuthzRuleException.Rule;
import com.flowbinary.platformservice.service.AuditService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Platform operators: memberships with no tenant, holding PLATFORM roles. Managed by
 * someone with {@code platform.operator.manage}, and never left without one.
 */
@Service
@RequiredArgsConstructor
public class PlatformMembershipService {

    private static final String MANAGE = "platform.operator.manage";

    /** An operator and the platform roles they hold; the first is the primary. */
    public record Operator(UUID userSub, String status, List<String> roles) {
    }

    private final MembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final AuthzRoleRepository roles;
    private final EffectivePermissionService permissions;
    private final MembershipService membershipService;
    private final AuthzEvents events;
    private final AuditService audit;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public List<Operator> list() {
        List<Operator> out = new ArrayList<>();
        for (Membership m : memberships.findByTenantIdIsNullOrderByCreatedAtAsc()) {
            out.add(new Operator(m.getUserSub(), m.getStatus().name(), roleCodes(m)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AuthzRole> platformRoles() {
        return roles.findByTenantIdIsNullOrderByNameAsc();
    }

    /**
     * Makes {@code userSub} an operator holding exactly {@code roleCodes} (first = primary), or changes
     * their roles. An empty list removes the platform membership.
     */
    @Transactional
    public Operator set(UUID userSub, List<String> roleCodes, UUID actor) {
        List<String> before = memberships.findPlatformMembership(userSub).map(this::roleCodes).orElse(List.of());
        if (roleCodes == null || roleCodes.isEmpty()) {
            memberships.findPlatformMembership(userSub).ifPresent(membershipService::remove);
            em.flush();
            someoneStillOperates();
            audit(actor, userSub, before, List.of());
            return new Operator(userSub, "REMOVED", List.of());
        }
        if (new HashSet<>(roleCodes).size() != roleCodes.size()) throw new IllegalArgumentException("Each role can be given once");
        List<AuthzRole> wanted = new ArrayList<>();
        for (String code : roleCodes) {
            wanted.add(roles.findByTenantIdIsNullAndCode(code)
                    .filter(r -> r.getKind() == RoleKind.PLATFORM && r.isActive())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown platform role: " + code)));
        }
        Membership m = membershipService.ensure(null, userSub, actor);
        m.setStatus(MembershipStatus.ACTIVE);
        List<RoleAssignment> old = assignments.findByMembershipId(m.getId());
        assignments.deleteAll(old);
        em.flush();
        for (int i = 0; i < wanted.size(); i++) {
            RoleAssignment a = new RoleAssignment();
            a.setMembershipId(m.getId());
            a.setRoleId(wanted.get(i).getId());
            a.setPrimary(i == 0);
            a.setAssignedBy(actor);
            assignments.save(a);
        }
        em.flush();
        someoneStillOperates();
        audit(actor, userSub, before, roleCodes);
        events.memberChanged(null, userSub, "OPERATOR_CHANGED");
        return new Operator(userSub, m.getStatus().name(), roleCodes);
    }

    private void someoneStillOperates() {
        for (Membership m : memberships.findByTenantIdIsNullOrderByCreatedAtAsc()) {
            if (m.getStatus() == MembershipStatus.ACTIVE
                    && permissions.forPlatform(m.getUserSub()).privileges().contains(MANAGE)) return;
        }
        throw new AuthzRuleException(Rule.LAST_ADMIN, "This change would leave nobody able to manage platform operators");
    }

    private List<String> roleCodes(Membership m) {
        List<RoleAssignment> held = assignments.findByMembershipId(m.getId());
        Map<UUID, AuthzRole> byId = new HashMap<>();
        roles.findByIdIn(held.stream().map(RoleAssignment::getRoleId).toList()).forEach(r -> byId.put(r.getId(), r));
        return held.stream().sorted(Comparator.comparing(RoleAssignment::isPrimary).reversed())
                .map(a -> byId.get(a.getRoleId())).filter(Objects::nonNull).map(AuthzRole::getCode).toList();
    }

    private void audit(UUID actor, UUID sub, List<String> before, List<String> after) {
        audit.record(new AuditService.Entry(null, actor, "AUTHZ_OPERATOR_CHANGED", "authz_membership", sub.toString(),
                Map.of("roles", before), Map.of("roles", after), null, "API"));
    }
}
