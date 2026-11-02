package com.flowbinary.platformservice.authz.web;

import com.flowbinary.platformservice.authz.model.*;
import com.flowbinary.platformservice.authz.service.MembershipService;
import com.flowbinary.platformservice.authz.service.PermissionSet;

import java.time.Instant;
import java.util.*;

/** Request and response bodies of {@code /api/v1/authz}. */
public final class AuthzDtos {

    private AuthzDtos() {
    }

    public record PermissionsResponse(UUID tenantId, String status, String primaryRole, List<String> roles,
                                      Set<String> privileges, Instant computedAt) {
        static PermissionsResponse of(PermissionSet s) {
            return new PermissionsResponse(s.tenantId(), s.status().name(), s.primaryRole(), s.roles(),
                    s.privileges(), s.computedAt());
        }
    }

    public record TemplateResponse(UUID id, String tenantType, String code, String name, String description,
                                   int version, boolean admin, List<String> privileges) {
        static TemplateResponse of(RoleTemplate t) {
            return new TemplateResponse(t.getId(), t.getTenantType(), t.getCode(), t.getName(), t.getDescription(),
                    t.getVersion(), t.isAdmin(), t.getPrivileges().stream().sorted().toList());
        }
    }

    public record PrivilegeResponse(String code, String ownerService, String name, String description, boolean phi,
                                    boolean clinicalWrite, String risk, String status) {
        static PrivilegeResponse of(AuthzPrivilege p) {
            return new PrivilegeResponse(p.getCode(), p.getOwnerService(), p.getName(), p.getDescription(), p.isPhi(),
                    p.isClinicalWrite(), p.getRisk(), p.getStatus());
        }
    }

    public record RoleResponse(UUID id, String code, String name, String description, String kind, UUID templateId,
                               boolean primaryEligible, Map<String, DeltaEffect> deltas, long holders) {
        static RoleResponse of(AuthzRole r, long holders) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getKind().name(),
                    r.getTemplateId(), r.isPrimaryEligible(), new TreeMap<>(r.getDeltas()), holders);
        }
    }

    public record CreateRoleRequest(String code, String name, String description, UUID templateId,
                                    Map<String, DeltaEffect> deltas) {
    }

    public record UpdateRoleRequest(String name, String description, Map<String, DeltaEffect> deltas) {
    }

    public record AssignmentResponse(UUID roleId, boolean primary, Instant validFrom, Instant validTo,
                                     List<UUID> locationIds) {
        static AssignmentResponse of(RoleAssignment a) {
            return new AssignmentResponse(a.getRoleId(), a.isPrimary(), a.getValidFrom(), a.getValidTo(),
                    a.getLocationIds());
        }
    }

    /** {@code name} and {@code email} come from the tenant's user directory; null when the user has no profile there. */
    public record MembershipResponse(UUID id, UUID userSub, String name, String email, String status,
                                     List<AssignmentResponse> assignments) {
        static MembershipResponse of(Membership m, List<RoleAssignment> held, String name, String email) {
            return new MembershipResponse(m.getId(), m.getUserSub(), name, email, m.getStatus().name(),
                    held.stream().map(AssignmentResponse::of).toList());
        }
    }

    public record ExtraRequest(UUID roleId, Instant validTo, List<UUID> locationIds) {
    }

    public record AssignmentsRequest(UUID primaryRoleId, List<ExtraRequest> extras) {
        MembershipService.AssignmentChange toChange() {
            List<MembershipService.Extra> list = extras == null ? List.of()
                    : extras.stream().map(e -> new MembershipService.Extra(e.roleId(), e.validTo(), e.locationIds())).toList();
            return new MembershipService.AssignmentChange(primaryRoleId, list);
        }
    }
}
