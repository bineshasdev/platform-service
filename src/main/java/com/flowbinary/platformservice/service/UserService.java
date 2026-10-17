package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.KeycloakProperties;
import com.flowbinary.platformservice.dto.user.ResetCredentialRequest;
import com.flowbinary.platformservice.dto.user.UserCreateRequest;
import com.flowbinary.platformservice.dto.user.UserResponse;
import com.flowbinary.platformservice.dto.user.UserUpdateRequest;
import com.flowbinary.platformservice.event.UserCreatedEvent;
import com.flowbinary.platformservice.event.UserDeletedEvent;
import com.flowbinary.platformservice.event.UserUpdatedEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.PlatformRole;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantUser;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_NOT_FOUND       = "User not found: ";
    private static final String NON_ALPHANUMERIC_RE  = "[^a-z0-9]";

    private final TenantUserRepository      tenantUserRepository;
    private final Keycloak                  keycloakAdmin;
    private final KeycloakProperties        kcProps;
    private final FileStorageService        fileStorageService;
    private final PlatformEventPublisher    eventPublisher;

    // ── Me ─────────────────────────────────────────────────────────────────

    public UserResponse getMe(Tenant tenant, UUID authUserId) {
        TenantUser user = tenantUserRepository.findByTenantIdAndAuthUserId(tenant.getId(), authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Current user not found in tenant"));
        return toResponse(user, tenant.getId());
    }

    @Transactional
    public UserResponse updateMe(Tenant tenant, UUID authUserId, UserUpdateRequest request) {
        TenantUser user = tenantUserRepository.findByTenantIdAndAuthUserId(tenant.getId(), authUserId)
                .orElseThrow(() -> new IllegalArgumentException("Current user not found in tenant"));

        // Self-update: only name and mobile are permitted (no role/status changes)
        List<String> changedFields = collectChangedFields(user, request);

        RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());
        syncKeycloakName(realm, user, request);
        applyFieldUpdates(user, request);

        user = tenantUserRepository.save(user);
        log.info("Profile self-updated: authUserId={} tenantId={} changed={}", authUserId, tenant.getId(), changedFields);

        UserUpdatedEvent updatedEvt = UserUpdatedEvent.builder()
                .occurredAt(Instant.now())
                .eventId(UUID.randomUUID())
                .tenantId(tenant.getId())
                .userId(user.getId())
                .authUserId(user.getAuthUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .status(user.getStatus())
                .changedFields(changedFields)
                .build();
        eventPublisher.publishUserUpdated(updatedEvt);

        return toResponse(user, tenant.getId());
    }

    // ── List ───────────────────────────────────────────────────────────────

    public Page<UserResponse> listUsers(Tenant tenant,
                                        int page, int size,
                                        String sortBy, String sortDir,
                                        String search) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);
        return tenantUserRepository.searchByTenant(tenant.getId(), search, pageable)
                .map(u -> toResponse(u, tenant.getId()));
    }

    // ── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse createUser(Tenant tenant, UserCreateRequest request) {
        if (tenantUserRepository.findByTenantIdAndEmail(tenant.getId(), request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered in this tenant: " + request.getEmail());
        }

        RealmResource realm        = keycloakAdmin.realm(kcProps.getAppRealm());
        String        kcUserId     = null;

        try {
            String username = generateUsername(tenant.getId(), request.getFirstName(), request.getLastName(), request.getEmail());
         
            // 1. Create Keycloak user with temporary password
            kcUserId = createKeycloakUser(realm,
                    username,
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getTemporaryPassword());

            // 2. Add to tenant's Keycloak organisation
            addMemberToOrganisation(realm, tenant.getTenantId(), kcUserId);

            // 3. Assign realm role
            assignRealmRole(realm, kcUserId, request.getRole());

            // 4. Persist TenantUser
            TenantUser user = TenantUser.builder()
                     
                    .tenant(tenant)
                    .authUserId(UUID.fromString(kcUserId))
                    .email(request.getEmail())
                    .username(username)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .role(request.getRole())
                    .mobile(request.getMobile())
                    .status("ACTIVE")
                    .build();
            user = tenantUserRepository.save(user);

            log.info("User created: email={} tenantId={} kcUserId={}", request.getEmail(), tenant.getId(), kcUserId);

            // 5. Publish Kafka events (fire-and-forget; does not affect the transaction)
            UserCreatedEvent createdEvt = UserCreatedEvent.builder()
               .occurredAt(Instant.now())
              .eventId(UUID.randomUUID())
                    .tenantId(tenant.getId())
                    .userId(user.getId())
                    .authUserId(user.getAuthUserId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .role(user.getRole())
                    .build();
            eventPublisher.publishUserCreated(createdEvt);

            return toResponse(user, tenant.getId());

        } catch (Exception ex) {
            log.error("User creation failed for email='{}': {}", request.getEmail(), ex.getMessage(), ex);
            if (kcUserId != null) {
                try {
                    realm.users().get(kcUserId).remove();
                    log.warn("Rolled back Keycloak user: {}", kcUserId);
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback Keycloak user {}: {}", kcUserId, rollbackEx.getMessage());
                }
            }
            throw ex;
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateUser(Tenant tenant, UUID userId, UserUpdateRequest request) {
        TenantUser user = tenantUserRepository.findById(userId)
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + userId));

        if (request.getRole() == PlatformRole.SUPERADMIN) {
            throw new IllegalArgumentException("Cannot assign SUPERADMIN role via this endpoint");
        }

        List<String> changedFields = collectChangedFields(user, request);

        RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());
        syncKeycloakName(realm, user, request);
        syncKeycloakRole(realm, user, request);
        applyFieldUpdates(user, request);

        user = tenantUserRepository.save(user);
        log.info("User updated: id={} tenantId={} changed={}", userId, tenant.getId(), changedFields);

        UserUpdatedEvent updatedEvt = UserUpdatedEvent.builder()
          .occurredAt(Instant.now())
         .eventId(UUID.randomUUID())
                .tenantId(tenant.getId())
                .userId(user.getId())
                .authUserId(user.getAuthUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .status(user.getStatus())
                .changedFields(changedFields)
                .build();
        eventPublisher.publishUserUpdated(updatedEvt);

        return toResponse(user, tenant.getId());
    }

    /**
     * Generates a unique username for the tenant.
     * Base form: lowercased firstName + lastName stripped to alphanumerics.
     * Falls back to the local part of the email when names are absent.
     * Appends an incrementing numeric suffix until the candidate is unique within the tenant.
     */
    private String generateUsername(UUID tenantId, String firstName, String lastName, String email) {
        String base;
        if (firstName != null && !firstName.isBlank()) {
            String fn = firstName.trim().toLowerCase().replaceAll(NON_ALPHANUMERIC_RE, "");
            String ln = lastName  != null ? lastName.trim().toLowerCase().replaceAll(NON_ALPHANUMERIC_RE, "") : "";
            base = fn + ln;
        } else {
            base = email.substring(0, email.indexOf('@')).toLowerCase().replaceAll(NON_ALPHANUMERIC_RE, "");
        }
        if (base.isBlank()) base = "user";

        // Single DB query — fetch all taken usernames with this prefix, resolve in memory
        Set<String> taken = tenantUserRepository.findLowercaseUsernamesWithPrefix(tenantId, base);
        if (!taken.contains(base)) return base;
        int suffix = 1;
        while (taken.contains(base + suffix)) suffix++;
        return base + suffix;
    }

    private static List<String> collectChangedFields(TenantUser user, UserUpdateRequest request) {
        List<String> changed = new ArrayList<>();
        if (request.getFirstName() != null && !request.getFirstName().equals(user.getFirstName())) changed.add("firstName");
        if (request.getLastName()  != null && !request.getLastName().equals(user.getLastName()))   changed.add("lastName");
        if (request.getRole()      != null && request.getRole() != user.getRole())                 changed.add("role");
        if (request.getMobile()    != null && !request.getMobile().equals(user.getMobile()))       changed.add("mobile");
        if (request.getStatus()    != null && !request.getStatus().equals(user.getStatus()))       changed.add("status");
        return changed;
    }

    private void syncKeycloakName(RealmResource realm, TenantUser user, UserUpdateRequest request) {
        if (request.getFirstName() == null && request.getLastName() == null) return;
        String kcId = user.getAuthUserId().toString();
        UserRepresentation ur = realm.users().get(kcId).toRepresentation();
        if (request.getFirstName() != null) ur.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) ur.setLastName(request.getLastName());
        realm.users().get(kcId).update(ur);
    }

    private void syncKeycloakRole(RealmResource realm, TenantUser user, UserUpdateRequest request) {
        if (request.getRole() == null || request.getRole() == user.getRole()) return;
        String kcId = user.getAuthUserId().toString();
        try {
            RoleRepresentation oldRole = realm.roles().get(user.getRole().roleName()).toRepresentation();
            realm.users().get(kcId).roles().realmLevel().remove(List.of(oldRole));
        } catch (Exception ex) {
            log.warn("Could not remove old role '{}' for user {}: {}", user.getRole(), user.getId(), ex.getMessage());
        }
        assignRealmRole(realm, kcId, request.getRole());
    }

    private static void applyFieldUpdates(TenantUser user, UserUpdateRequest request) {
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) user.setLastName(request.getLastName());
        if (request.getRole()      != null) user.setRole(request.getRole());
        if (request.getMobile()    != null) user.setMobile(request.getMobile());
        if (request.getStatus()    != null) user.setStatus(request.getStatus());
    }

    // ── Reset credentials ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void resetCredentials(Tenant tenant, UUID userId, ResetCredentialRequest request) {
        TenantUser user = tenantUserRepository.findById(userId)
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + userId));

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(request.isTemporary());

        keycloakAdmin.realm(kcProps.getAppRealm())
                .users().get(user.getAuthUserId().toString())
                .resetPassword(credential);

        log.info("Credentials reset for user: id={} tenantId={} temporary={}",
                userId, tenant.getId(), request.isTemporary());
    }

    // ── Delete ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteUser(Tenant tenant, UUID userId) {
        TenantUser user = tenantUserRepository.findById(userId)
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + userId));

        RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());
        try {
            realm.users().get(user.getAuthUserId().toString()).remove();
            log.info("Removed Keycloak user: {}", user.getAuthUserId());
        } catch (Exception ex) {
            log.warn("Could not remove Keycloak user {}: {}", user.getAuthUserId(), ex.getMessage());
        }

        tenantUserRepository.delete(user);
        log.info("Deleted user: id={} email={}", userId, user.getEmail());

        UserDeletedEvent deletedEvt = UserDeletedEvent.builder()
          .occurredAt(Instant.now())
         .eventId(UUID.randomUUID())
                .tenantId(tenant.getId())
                .userId(user.getId())
                .authUserId(user.getAuthUserId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
        eventPublisher.publishUserDeleted(deletedEvt);
    }

    // ── Keycloak helpers ───────────────────────────────────────────────────

    private String createKeycloakUser(RealmResource realm,
                                       String username, String email,
                                       String firstName, String lastName,
                                       String temporaryPassword) {
        UserRepresentation ur = new UserRepresentation();
        ur.setUsername(username);
        ur.setEmail(email);
        ur.setFirstName(firstName);
        ur.setLastName(lastName);
        ur.setEnabled(true);
        ur.setEmailVerified(true);
        ur.setRequiredActions(List.of("UPDATE_PASSWORD"));

        try (Response response = realm.users().create(ur)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException(
                        "Keycloak user creation failed with status " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(temporaryPassword);
            credential.setTemporary(true);
            realm.users().get(userId).resetPassword(credential);

            return userId;
        }
    }

    private void addMemberToOrganisation(RealmResource realm, String orgId, String userId) {
        try (Response response = realm.organizations().get(orgId).members().addMember(userId)) {
            if (response.getStatus() != 201 && response.getStatus() != 204) {
                throw new IllegalStateException(
                        "Adding user to org failed with status " + response.getStatus());
            }
        }
    }

    private void assignRealmRole(RealmResource realm, String userId, PlatformRole platformRole) {
        RoleRepresentation role = realm.roles().get(platformRole.roleName()).toRepresentation();
        realm.users().get(userId).roles().realmLevel().add(List.of(role));
        log.debug("Assigned realm role '{}' to user {}", platformRole.roleName(), userId);
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    // ── Profile image ──────────────────────────────────────────────────────

    @Transactional
    public UserResponse uploadProfileImage(Tenant tenant, UUID userId,
                                           UUID callerId,
                                           org.springframework.web.multipart.MultipartFile file) {
        TenantUser user = tenantUserRepository.findById(userId)
                .filter(u -> u.getTenant().getId().equals(tenant.getId()))
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + userId));

        // Delete the old profile image from storage (best-effort; don't fail the upload)
        if (user.getProfileImage() != null) {
            try {
                fileStorageService.delete(tenant, user.getProfileImage().getId());
            } catch (Exception ex) {
                log.warn("Could not delete old profile image {}: {}", user.getProfileImage().getId(), ex.getMessage());
            }
        }

        // Upload via FileStorageService — respects tenant's configured backend (LOCAL / S3 / DATABASE)
        com.flowbinary.platformservice.model.TenantFile stored =
                fileStorageService.upload(tenant, callerId,
                        com.flowbinary.platformservice.model.FileType.PROFILE_IMAGE, file);

        // Resolve the URL: prefer public URL (S3), fall back to in-app download path
        String imageUrl = stored.getPublicUrl() != null
                ? stored.getPublicUrl()
                : "/api/v1/files/" + stored.getId() + "/download";

        user.setProfileImage(stored);
        user.setProfileImageUrl(imageUrl);
        user = tenantUserRepository.save(user);

        log.info("Profile image updated: userId={} fileId={} backend={}",
                userId, stored.getId(), stored.getBackend());
        return toResponse(user, tenant.getId());
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private UserResponse toResponse(TenantUser u, UUID tenantId) {
        UUID profileImageId = u.getProfileImage() != null ? u.getProfileImage().getId() : null;
        return UserResponse.builder()
                .id(u.getId())
                .authUserId(u.getAuthUserId())
                .tenantId(tenantId)
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .mobile(u.getMobile())
                .status(u.getStatus())
                .profileImageId(profileImageId)
                .profileImageUrl(u.getProfileImageUrl())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
