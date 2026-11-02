package com.flowbinary.platformservice.authz.web;

import com.flowbinary.platformservice.authz.sdk.Authz;
import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.MembershipService;
import com.flowbinary.platformservice.authz.service.PlatformMembershipService;
import com.flowbinary.platformservice.authz.service.TemplateProposalService;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.authz.service.RoleAdminService;
import com.flowbinary.platformservice.authz.web.AuthzDtos.*;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.flowbinary.platformservice.model.TenantUser;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The Authorization service API (design §14, phase 1 subset). Tenant-scoped
 * endpoints act on the tenant pinned in the caller's token.
 */
@RestController
@RequestMapping("/api/v1/authz")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "Roles, memberships and effective permissions")
public class AuthzController {

    private final Authz authz;
    private final TenantContextResolver tenantContext;
    private final TenantRepository tenants;
    private final EffectivePermissionService permissions;
    private final RoleAdminService roleAdmin;
    private final MembershipService memberships;
    private final PlatformMembershipService platformMembers;
    private final TemplateProposalService proposals;
    private final TenantUserRepository tenantUsers;

    // ── Effective permissions ────────────────────────────────────────────

    @GetMapping("/me/permissions")
    @Operation(summary = "The caller's privileges in the tenant pinned in their token")
    PermissionsResponse myPermissions(Authentication auth) {
        return PermissionsResponse.of(authz.current(auth));
    }

    /** For other services' SDKs: {@code tenantId} is the Keycloak organization id from the user's token. */
    @GetMapping("/principals/{sub}/tenants/{tenantId}/permissions")
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "A user's privileges in a tenant (service clients only)")
    PermissionsResponse principalPermissions(@PathVariable UUID sub, @PathVariable String tenantId) {
        Tenant tenant = tenants.findByTenantId(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
        return PermissionsResponse.of(permissions.forTenant(tenant.getId(), sub));
    }

    /** For other services' SDKs: what an operator may do on the platform. */
    @GetMapping("/principals/{sub}/platform/permissions")
    @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")
    @Operation(summary = "A user's platform (operator) privileges (service clients only)")
    PermissionsResponse platformPermissions(@PathVariable UUID sub) {
        return PermissionsResponse.of(permissions.forPlatform(sub));
    }

    // ── Catalogue and templates ──────────────────────────────────────────

    @GetMapping("/templates")
    @RequiresPrivilege("platform.role.manage")
    @Operation(summary = "Role templates of the caller's tenant type")
    List<TemplateResponse> templates(Authentication auth) {
        return roleAdmin.templates(tenant(auth)).stream().map(TemplateResponse::of).toList();
    }

    @GetMapping("/privileges")
    @RequiresPrivilege("platform.role.manage")
    @Operation(summary = "Privileges a role of the caller's tenant may contain")
    List<PrivilegeResponse> privileges(Authentication auth) {
        return roleAdmin.assignablePrivileges(tenant(auth)).stream().map(PrivilegeResponse::of).toList();
    }

    // ── Roles ────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    @RequiresPrivilege("platform.role.manage")
    List<RoleResponse> roles(Authentication auth) {
        return roleAdmin.roles(tenant(auth)).stream()
                .map(r -> RoleResponse.of(r, roleAdmin.holders(r.getId())))
                .toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPrivilege("platform.role.manage")
    @Operation(summary = "Create a derived (templateId set) or custom role")
    RoleResponse createRole(Authentication auth, @RequestBody CreateRoleRequest req) {
        var role = roleAdmin.create(tenant(auth), new RoleAdminService.CreateRole(req.code(), req.name(),
                req.description(), req.templateId(), req.deltas()), authz.current(auth), actor(auth));
        return RoleResponse.of(role, 0);
    }

    @PatchMapping("/roles/{id}")
    @RequiresPrivilege("platform.role.manage")
    RoleResponse updateRole(Authentication auth, @PathVariable UUID id, @RequestBody UpdateRoleRequest req) {
        var role = roleAdmin.update(tenant(auth), id,
                new RoleAdminService.UpdateRole(req.name(), req.description(), req.deltas()),
                authz.current(auth), actor(auth));
        return RoleResponse.of(role, roleAdmin.holders(id));
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPrivilege("platform.role.manage")
    void deleteRole(Authentication auth, @PathVariable UUID id) {
        roleAdmin.delete(tenant(auth), id, actor(auth));
    }

    // ── Memberships ──────────────────────────────────────────────────────

    @GetMapping("/memberships")
    @RequiresPrivilege("platform.member.manage")
    List<MembershipResponse> memberships(Authentication auth) {
        Tenant tenant = tenant(auth);
        List<com.flowbinary.platformservice.authz.model.Membership> found = memberships.list(tenant);
        Map<UUID, TenantUser> people = tenantUsers
                .findByTenantIdAndAuthUserIdIn(tenant.getId(), found.stream().map(m -> m.getUserSub()).toList()).stream()
                .collect(Collectors.toMap(TenantUser::getAuthUserId, u -> u, (a, b) -> a));
        return found.stream().map(m -> {
            TenantUser u = people.get(m.getUserSub());
            String name = u == null ? null : ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                    + (u.getLastName() == null ? "" : u.getLastName())).trim();
            return MembershipResponse.of(m, memberships.assignmentsOf(m.getId()), name == null || name.isBlank() ? null : name,
                    u == null ? null : u.getEmail());
        }).toList();
    }

    @PutMapping("/memberships/{id}/assignments")
    @RequiresPrivilege("platform.member.manage")
    @Operation(summary = "Replace a member's primary and extra roles")
    MembershipResponse replaceAssignments(Authentication auth, @PathVariable UUID id,
                                          @RequestBody AssignmentsRequest req) {
        Tenant tenant = tenant(auth);
        var saved = memberships.replaceAssignments(tenant, id, req.toChange(), authz.current(auth), actor(auth));
        return MembershipResponse.of(memberships.get(tenant, id), saved, null, null);
    }

    @GetMapping("/memberships/{id}/explain")
    @RequiresPrivilege("platform.member.manage")
    @Operation(summary = "Why a member has or lacks a privilege")
    MembershipService.Explanation explain(Authentication auth, @PathVariable UUID id, @RequestParam String privilege) {
        return memberships.explain(tenant(auth), id, privilege);
    }

    // ── Service manifests: operator side ─────────────────────────────────

    public record RejectRequest(String reason) {
    }

    @GetMapping("/services")
    @RequiresPrivilege("platform.template.manage")
    @Operation(summary = "Services that declare privileges, with counts and the last manifest")
    List<TemplateProposalService.ServiceSummary> services() {
        return proposals.services();
    }

    @GetMapping("/proposals")
    @RequiresPrivilege("platform.template.manage")
    @Operation(summary = "Template grants services have proposed")
    List<TemplateProposalService.ProposalView> proposals(@RequestParam(required = false) String status) {
        return proposals.list(status);
    }

    @PostMapping("/proposals/{id}/approve")
    @RequiresPrivilege("platform.template.manage")
    @Operation(summary = "Add the proposed privilege to the template")
    TemplateProposalService.ProposalView approve(Authentication auth, @PathVariable UUID id) {
        return proposals.approve(id, actor(auth));
    }

    @PostMapping("/proposals/{id}/reject")
    @RequiresPrivilege("platform.template.manage")
    TemplateProposalService.ProposalView reject(Authentication auth, @PathVariable UUID id, @RequestBody(required = false) RejectRequest req) {
        return proposals.reject(id, actor(auth), req == null ? null : req.reason());
    }

    // ── Platform operators ───────────────────────────────────────────────

    public record OperatorRequest(List<String> roles) {
    }

    @GetMapping("/platform/members")
    @RequiresPrivilege("platform.operator.manage")
    @Operation(summary = "Platform operators and their platform roles")
    List<PlatformMembershipService.Operator> operators() {
        return platformMembers.list();
    }

    @GetMapping("/platform/roles")
    @RequiresPrivilege("platform.operator.manage")
    List<String> platformRoles() {
        return platformMembers.platformRoles().stream().map(r -> r.getCode()).toList();
    }

    @PutMapping("/platform/members/{sub}")
    @RequiresPrivilege("platform.operator.manage")
    @Operation(summary = "Make a user an operator with these platform roles (first is primary); an empty list removes them")
    PlatformMembershipService.Operator setOperator(Authentication auth, @PathVariable UUID sub,
                                                   @RequestBody OperatorRequest req) {
        return platformMembers.set(sub, req.roles(), actor(auth));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Tenant tenant(Authentication auth) {
        return tenantContext.resolveRequired(auth);
    }

    private static UUID actor(Authentication auth) {
        return UUID.fromString(((JwtAuthenticationToken) auth).getToken().getSubject());
    }
}
