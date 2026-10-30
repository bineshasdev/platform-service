package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.authz.service.LegacyRoleBridge;
import com.flowbinary.platformservice.config.KeycloakProperties;
import com.flowbinary.platformservice.dto.ResolveTenantResponse;
import com.flowbinary.platformservice.dto.TenantSignupRequest;
import com.flowbinary.platformservice.dto.TenantSignupResponse;
import com.flowbinary.platformservice.event.TenantCreatedEvent;
import com.flowbinary.platformservice.messaging.PlatformEventPublisher;
import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.*;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository             tenantRepository;
    private final TenantUserRepository         tenantUserRepository;
    private final PlanRepository               planRepository;
    private final TenantSubscriptionRepository subscriptionRepository;
    private final PlatformEventPublisher       eventPublisher;
    private final Keycloak                     keycloakAdmin;
    private final KeycloakProperties           kcProps;
    private final LegacyRoleBridge             authzBridge;

    /**
     * Registers a new tenant (Keycloak organisation) and its first admin user.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate slug / email uniqueness</li>
     *   <li>Create Keycloak organisation in the app realm</li>
     *   <li>Create Keycloak admin user ({@code {slug}.admin})</li>
     *   <li>Add the user to the organisation</li>
     *   <li>Persist {@link Tenant} and {@link TenantUser} to the platform DB</li>
     *   <li>Assign the free plan subscription</li>
     *   <li>Publish {@link TenantCreatedEvent}</li>
     * </ol>
     *
     * <p>If any step after Keycloak resource creation fails, the Keycloak
     * resources are cleaned up to avoid orphaned data.
     */
    @Transactional
    public TenantSignupResponse signup(TenantSignupRequest request) {
        String slug = uniqueSlug(generateSlug(request.getTenantName()));
        if (tenantRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getAdminEmail());
        }

        String keycloakOrgId  = null;
        String keycloakUserId = null;

        try {
            RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());

            // ── 1. Create Keycloak organisation ───────────────────
            keycloakOrgId = createOrganisation(realm, slug,
                    resolvedDisplayName(request));

            // ── 2. Create Keycloak admin user ─────────────────────
            String adminUsername = slug +"admin";
            keycloakUserId = createUser(realm, adminUsername,
                    request.getAdminEmail(),
                    request.getAdminFirstName(),
                    request.getAdminLastName(),
                    request.getAdminPassword());

            // ── 3. Add user to organisation ───────────────────────
            addMemberToOrganisation(realm, keycloakOrgId, keycloakUserId);

            // ── 3b. Assign tenantadmin realm role ─────────────────
            assignRealmRole(realm, keycloakUserId, PlatformRole.TENANTADMIN);

            // ── 4. Persist Tenant ─────────────────────────────────
            Tenant tenant = Tenant.builder()
                    .tenantId(keycloakOrgId)
                    .name(request.getTenantName())
                    .displayName(resolvedDisplayName(request))
                    .email(request.getAdminEmail())
                    .slug(slug)
                    .plan(TenantPlan.free)
                    .status(TenantStatus.active)
                    .effectiveStartDate(Instant.now())
                    .build();
            tenant = tenantRepository.save(tenant);

            // ── 5. Persist admin TenantUser ───────────────────────
            TenantUser adminUser = TenantUser.builder()
                    .tenant(tenant)
                    .authUserId(UUID.fromString(keycloakUserId))
                    .username(adminUsername)
                    .email(request.getAdminEmail())
                    .firstName(request.getAdminFirstName())
                    .lastName(request.getAdminLastName())
                    .role(PlatformRole.TENANTADMIN)
                    .status("ACTIVE")
                    .build();
            adminUser = tenantUserRepository.save(adminUser);
            authzBridge.onTenantCreated(tenant, adminUser.getAuthUserId());

            // ── 6. Assign free plan ───────────────────────────────
            assignFreePlan(tenant);

            // ── 7. Publish domain event ───────────────────────────
            eventPublisher.publishTenantCreated(TenantCreatedEvent.builder()
                    .tenantId(tenant.getId())
                    .tenantName(tenant.getName())
                    .slug(tenant.getSlug())
                    .email(tenant.getEmail())
                    .plan(tenant.getPlan().name())
                    .build());

            log.info("Tenant created: slug={} keycloakOrgId={} adminUser={}",
                    slug, keycloakOrgId, adminUsername);

            return TenantSignupResponse.builder()
                    .tenantId(tenant.getId())
                    .tenantName(tenant.getName())
                    .displayName(tenant.getDisplayName())
                    .slug(tenant.getSlug())
                    .keycloakOrgId(keycloakOrgId)
                    .adminUserId(adminUser.getId())
                    .adminUsername(adminUsername)
                    .keycloakUserId(keycloakUserId)
                    .plan(tenant.getPlan().name())
                    .createdAt(tenant.getCreatedAt())
                    .build();

        } catch (Exception ex) {
            log.error("Signup failed for slug='{}': {}", slug, ex.getMessage(), ex);
            rollbackKeycloak(keycloakOrgId, keycloakUserId);
            throw ex;
        }
    }

    /**
     * Resolves a tenant by email or slug.
     */
    @Transactional(readOnly = true)
    public ResolveTenantResponse resolveTenant(String identifier) {
        Tenant tenant = tenantUserRepository.findFirstByEmailOrUsernameIgnoreCase(identifier)
                .map(TenantUser::getTenant)
                .orElseThrow(() -> new IllegalArgumentException("No user found for identifier: " + identifier));

        return ResolveTenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .displayName(tenant.getDisplayName())
                .slug(tenant.getSlug())
                .realmName(kcProps.getAppRealm())
                .keycloakServerUrl(kcProps.getServerUrl())
                .uiClientId("platform-ui")
                .plan(tenant.getPlan().name())
                .build();
    }

    // ── Keycloak helpers ───────────────────────────────────────────────

    private String createOrganisation(RealmResource realm, String slug, String displayName) {
        OrganizationRepresentation org = new OrganizationRepresentation();
        org.setName(displayName);
        org.setAlias(slug);
        org.setEnabled(true);

        try (Response response = realm.organizations().create(org)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException(
                        "Keycloak org creation failed with status " + response.getStatus());
            }
            String orgId = CreatedResponseUtil.getCreatedId(response);
            log.debug("Keycloak org created: alias={} id={}", slug, orgId);
            return orgId;
        }
    }

    private String createUser(RealmResource realm, String username, String email,
                              String firstName, String lastName, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        try (Response response = realm.users().create(user)) {
            if (response.getStatus() != 201) {
                throw new IllegalStateException(
                        "Keycloak user creation failed with status " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false); 
            user.setCredentials(List.of(credential)); 
            user.setRequiredActions(List.of("UPDATE_PASSWORD")); 
            realm.users().get(userId).resetPassword(credential);

            log.debug("Keycloak user created: username={} id={}", username, userId);
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

    /** Best-effort cleanup of Keycloak resources when the DB transaction fails. */
    private void rollbackKeycloak(String orgId, String userId) {
        RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());
        if (userId != null) {
            try {
                realm.users().get(userId).remove();
                log.warn("Rolled back Keycloak user: {}", userId);
            } catch (Exception e) {
                log.error("Failed to rollback Keycloak user {}: {}", userId, e.getMessage());
            }
        }
        if (orgId != null) {
            try {
                realm.organizations().get(orgId).delete();
                log.warn("Rolled back Keycloak org: {}", orgId);
            } catch (Exception e) {
                log.error("Failed to rollback Keycloak org {}: {}", orgId, e.getMessage());
            }
        }
    }

    // ── Subscription helper ────────────────────────────────────────────

    private void assignFreePlan(Tenant tenant) {
        planRepository.findByCode(TenantPlan.free).ifPresentOrElse(
                freePlan -> {
                    TenantSubscription subscription = TenantSubscription.builder()
                            .tenant(tenant)
                            .plan(freePlan)
                            .status(SubscriptionStatus.ACTIVE)
                            .currentPeriodStart(Instant.now())
                            .build();
                    subscriptionRepository.save(subscription);
                    log.debug("Free plan assigned to tenant {}", tenant.getSlug());
                },
                () -> log.warn("Free plan not found in DB — skipping subscription for tenant '{}'." +
                        " Seed the plans table to enable automatic assignment.", tenant.getSlug())
        );
    }

    // ── Slug utility ───────────────────────────────────────────────────

    static String generateSlug(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]+", "-")
                   .replaceAll("(^-+)|(-+$)", "");
    }

    /** Returns {@code base} if available, otherwise {@code base-2}, {@code base-3}, … */
    private String uniqueSlug(String base) {
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }
        int suffix = 2;
        String candidate;
        do {
            candidate = base + "-" + suffix++;
        } while (tenantRepository.existsBySlug(candidate));
        return candidate;
    }

    private static String resolvedDisplayName(TenantSignupRequest r) {
        return StringUtils.hasText(r.getDisplayName()) ? r.getDisplayName() : r.getTenantName();
    }
}
