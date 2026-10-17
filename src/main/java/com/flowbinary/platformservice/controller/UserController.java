package com.flowbinary.platformservice.controller;

import com.flowbinary.platformservice.dto.user.ResetCredentialRequest;
import com.flowbinary.platformservice.dto.user.UserCreateRequest;
import com.flowbinary.platformservice.dto.user.UserResponse;
import com.flowbinary.platformservice.dto.user.UserUpdateRequest;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Tenant user management")
public class UserController {

    private final UserService           userService;
    private final TenantContextResolver tenantContextResolver;

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's own profile")
    UserResponse getMe(Authentication authentication) {
        Tenant tenant     = tenantContextResolver.resolveRequired(authentication);
        UUID   authUserId = extractUserId(authentication);
        return userService.getMe(tenant, authUserId);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's own profile (name, mobile)")
    UserResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody UserUpdateRequest request) {
        Tenant tenant     = tenantContextResolver.resolveRequired(authentication);
        UUID   authUserId = extractUserId(authentication);
        return userService.updateMe(tenant, authUserId, request);
    }

    @GetMapping
    @Operation(summary = "List users for the current tenant (paginated)")
    Page<UserResponse> listUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0")        int    page,
            @RequestParam(defaultValue = "10")       int    size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")     String sortDir,
            @RequestParam(defaultValue = "")         String search) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return userService.listUsers(tenant, page, size, sortBy, sortDir, search);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new user under the current tenant")
    UserResponse createUser(
            Authentication authentication,
            @Valid @RequestBody UserCreateRequest request) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return userService.createUser(tenant, request);
    }

    @PatchMapping("/{id}")
    //@PreAuthorize("hasAnyAuthority('ROLE_SUPERADMIN','ROLE_TENANTADMIN', 'ROLE_USER')")
    @Operation(summary = "Update a user's profile, role, or status")
    UserResponse updateUser(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return userService.updateUser(tenant, id, request);
    }

    @PostMapping(value = "/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a user's profile image")
    UserResponse uploadProfileImage(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        Tenant tenant   = tenantContextResolver.resolveRequired(authentication);
        UUID   callerId = extractUserId(authentication);
        return userService.uploadProfileImage(tenant, id, callerId, file);
    }

    @PostMapping("/{id}/reset-credentials")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPERADMIN','ROLE_TENANTADMIN','ROLE_CLINIC_ADMIN')")
    @Operation(summary = "Reset a user's password (issues a temporary password by default)")
    void resetCredentials(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ResetCredentialRequest request) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        userService.resetCredentials(tenant, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user from the current tenant")
    void deleteUser(
            Authentication authentication,
            @PathVariable UUID id) {
        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        userService.deleteUser(tenant, id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            String sub = jwt.getSubject();
            return sub != null ? UUID.fromString(sub) : null;
        }
        return null;
    }
}
