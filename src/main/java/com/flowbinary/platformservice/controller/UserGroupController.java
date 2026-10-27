package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.menu.GroupRequest;
import com.flowbinary.platformservice.dto.menu.GroupResponse;
import com.flowbinary.platformservice.dto.menu.MemberResponse;
import com.flowbinary.platformservice.dto.menu.MembersRequest;
import com.flowbinary.platformservice.service.MenuAdminAccess;
import com.flowbinary.platformservice.service.MenuAdminAccess.AdminContext;
import com.flowbinary.platformservice.service.MenuAdminAccess.ScopeKind;
import com.flowbinary.platformservice.service.MenuResolutionService;
import com.flowbinary.platformservice.service.UserGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Tenant user groups used to target menu items. Always the caller's own
 * tenant; only a platform operator may name another with {@code tenantId}.
 */
@RestController
@RequestMapping("/api/v1/admin/user-groups")
@RequiredArgsConstructor
@Tag(name = "User groups", description = "Tenant-scoped groups for menu targeting")
public class UserGroupController {

    private static final String APP = MenuResolutionService.DEFAULT_APPLICATION;

    private final MenuAdminAccess access;
    private final UserGroupService service;

    private AdminContext ctx(Authentication auth, String application, UUID tenantId) {
        return access.resolve(auth, application, ScopeKind.TENANT, tenantId);
    }

    @GetMapping
    @Operation(summary = "List the tenant's groups for an application")
    public List<GroupResponse> list(Authentication auth, @RequestParam(defaultValue = APP) String application,
                                    @RequestParam(required = false) UUID tenantId) {
        return service.list(ctx(auth, application, tenantId));
    }

    @PostMapping
    @Operation(summary = "Create a group")
    public ResponseEntity<GroupResponse> create(Authentication auth, @Valid @RequestBody GroupRequest body,
                                                @RequestParam(defaultValue = APP) String application,
                                                @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ctx(auth, application, tenantId), body));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Rename, describe, activate or deactivate a group")
    public GroupResponse update(Authentication auth, @PathVariable String code, @Valid @RequestBody GroupRequest body,
                                @RequestParam(defaultValue = APP) String application,
                                @RequestParam(required = false) UUID tenantId) {
        return service.update(ctx(auth, application, tenantId), code, body);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete a group not used by any menu assignment")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String code,
                                       @RequestParam(defaultValue = APP) String application,
                                       @RequestParam(required = false) UUID tenantId) {
        service.delete(ctx(auth, application, tenantId), code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/members")
    public List<MemberResponse> members(Authentication auth, @PathVariable String code,
                                        @RequestParam(defaultValue = APP) String application,
                                        @RequestParam(required = false) UUID tenantId) {
        return service.members(ctx(auth, application, tenantId), code);
    }

    @PostMapping("/{code}/members")
    @Operation(summary = "Add users of this tenant to the group")
    public List<MemberResponse> addMembers(Authentication auth, @PathVariable String code,
                                           @Valid @RequestBody MembersRequest body,
                                           @RequestParam(defaultValue = APP) String application,
                                           @RequestParam(required = false) UUID tenantId) {
        return service.addMembers(ctx(auth, application, tenantId), code, body.userIds());
    }

    @DeleteMapping("/{code}/members/{userId}")
    public ResponseEntity<Void> removeMember(Authentication auth, @PathVariable String code, @PathVariable UUID userId,
                                             @RequestParam(defaultValue = APP) String application,
                                             @RequestParam(required = false) UUID tenantId) {
        service.removeMember(ctx(auth, application, tenantId), code, userId);
        return ResponseEntity.noContent().build();
    }
}
