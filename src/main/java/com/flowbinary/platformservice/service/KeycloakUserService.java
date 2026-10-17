package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.config.KeycloakProperties;
import com.flowbinary.platformservice.model.PlatformRole;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Thin wrapper over the Keycloak admin client for the app realm — shared by
 * every signup flow (clinic / doctor / patient) so the provisioning logic lives
 * in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak           keycloakAdmin;
    private final KeycloakProperties kcProps;

    private RealmResource realm() {
        return keycloakAdmin.realm(kcProps.getAppRealm());
    }

    // ── Organisations (tenant signups only) ───────────────────────────────

    /**
     * Attribute tagged on an organisation from the moment it's created until the
     * caller's local records (Tenant/TenantUser/etc.) have committed and it calls
     * {@link #finalizeOrganisation}. Lets {@code KeycloakOrganisationReconciliationJob}
     * find orgs whose registration crashed mid-flight without relying on the display
     * name, which stays correct throughout.
     */
    static final String ATTR_PROVISIONING_STATUS    = "provisioningStatus";
    static final String ATTR_PROVISIONING_STARTED_AT = "provisioningStartedAt";
    static final String STATUS_PENDING              = "PENDING";

    /**
     * How old a still-pending org must be before a conflicting create() is allowed to
     * reap it inline. Must stay well below the background sweep's staleness window
     * (30 min) — this only exists to unblock a user retrying their own crashed
     * attempt quickly — but well above how long a healthy create-org→save-tenant path
     * ever takes, so a genuinely concurrent in-flight registration for the same slug
     * is never mistaken for an orphan and torn out from under it.
     */
    private static final Duration MIN_ORPHAN_AGE_FOR_INLINE_REAP = Duration.ofMinutes(2);

    /**
     * Creates a Keycloak organisation, tagged pending, and returns its id.
     *
     * <p>The org's {@code name} is internal-only — nothing in Keycloak surfaces it to
     * end users — so it's set to {@code alias} rather than the clinic's display name.
     * Clinics collide on display name constantly (multiple "City Hospital"s are
     * expected and allowed); {@code alias} is the slug callers already guarantee
     * unique via {@code uniqueSlug()}, so reusing it here sidesteps organisation-name
     * conflicts entirely instead of needing to detect and work around them.
     */
    public String createOrganisation(String alias) {
        OrganizationRepresentation org = new OrganizationRepresentation();
        org.setName(alias);
        org.setAlias(alias);
        org.setEnabled(true);
        org.singleAttribute(ATTR_PROVISIONING_STATUS, STATUS_PENDING);
        org.singleAttribute(ATTR_PROVISIONING_STARTED_AT, Instant.now().toString());

        try (Response response = realm().organizations().create(org)) {
            if (response.getStatus() == 201) {
                return CreatedResponseUtil.getCreatedId(response);
            }
            if (response.getStatus() != 409) {
                throw new IllegalStateException("Keycloak org creation failed with status " + response.getStatus());
            }
        }

        // Alias taken. Usually that's a genuine duplicate, but it's also exactly what
        // a crashed prior attempt looks like (org created, Tenant row never committed,
        // so uniqueSlug() had no way to know and handed out the same slug again).
        // Reap it and retry once if that's what happened.
        reapOrphanAtAliasOrThrow(alias);
        try (Response retry = realm().organizations().create(org)) {
            if (retry.getStatus() != 201) {
                throw new IllegalStateException("Keycloak org creation failed with status " + retry.getStatus()
                        + " for slug '" + alias + "' even after reaping a stale organisation.");
            }
            return CreatedResponseUtil.getCreatedId(retry);
        }
    }

    /**
     * Finds the pending org holding {@code alias}, if any, and deletes it when it's
     * old enough to be safely assumed abandoned. Throws rather than deletes whenever
     * that's not clearly safe — a real conflict (active tenant) or an in-flight one
     * (too recent) both fall through to a caller-facing error instead.
     */
    private void reapOrphanAtAliasOrThrow(String alias) {
        OrganizationRepresentation stale = findPendingOrganisations().stream()
                .filter(o -> alias.equals(o.getAlias()))
                .findFirst()
                .orElseThrow(() -> new OrganisationAliasTakenException(alias));

        Instant startedAt = provisioningStartedAt(stale);
        if (startedAt == null || Instant.now().isBefore(startedAt.plus(MIN_ORPHAN_AGE_FOR_INLINE_REAP))) {
            throw new IllegalStateException(
                    "An organisation with slug '" + alias + "' is already being provisioned. Please try again in a couple of minutes.");
        }

        log.warn("Reaping orphaned Keycloak org {} (alias '{}'), pending since {}, to unblock a new registration attempt.",
                stale.getId(), alias, startedAt);
        listMemberIds(stale.getId()).forEach(this::deleteUserQuietly);
        deleteOrganisationQuietly(stale.getId());
    }

    /** Clears the pending tag once the caller's local records have committed. */
    public void finalizeOrganisation(String orgId) {
        OrganizationResource resource = realm().organizations().get(orgId);
        OrganizationRepresentation org = resource.toRepresentation();
        if (org.getAttributes() != null) {
            org.getAttributes().remove(ATTR_PROVISIONING_STATUS);
            org.getAttributes().remove(ATTR_PROVISIONING_STARTED_AT);
        }
        try (Response response = resource.update(org)) {
            if (response.getStatus() != 200 && response.getStatus() != 204) {
                throw new IllegalStateException("Keycloak org finalize failed with status " + response.getStatus());
            }
        }
    }

    /** Organisations still tagged pending — registration for them never finalized. */
    public List<OrganizationRepresentation> findPendingOrganisations() {
        return realm().organizations().searchByAttribute(ATTR_PROVISIONING_STATUS + ":" + STATUS_PENDING);
    }

    /** Keycloak user ids belonging to an organisation (org deletion doesn't remove member accounts). */
    public List<String> listMemberIds(String orgId) {
        return realm().organizations().get(orgId).members().list(null, null)
                .stream().map(MemberRepresentation::getId).toList();
    }

    /** When an organisation was tagged pending, or {@code null} if untagged/unparseable. */
    public static Instant provisioningStartedAt(OrganizationRepresentation org) {
        List<String> values = org.getAttributes() == null
                ? null : org.getAttributes().get(ATTR_PROVISIONING_STARTED_AT);
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(values.get(0));
        } catch (Exception ex) {
            return null;
        }
    }

    public void addOrganisationMember(String orgId, String userId) {
        try (Response response = realm().organizations().get(orgId).members().addMember(userId)) {
            if (response.getStatus() != 201 && response.getStatus() != 204) {
                throw new IllegalStateException("Adding user to org failed with status " + response.getStatus());
            }
        }
    }

    // ── Users ─────────────────────────────────────────────────────────────

    /**
     * Creates a realm user with a permanent password and returns its id.
     * Not added to any organisation — callers that need org membership call
     * {@link #addOrganisationMember} afterwards.
     */
    public String createUser(String username, String email, String firstName, String lastName,
                             String password, boolean emailVerified) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(emailVerified);

        try (Response response = realm().users().create(user)) {
            if (response.getStatus() == 409) {
                throw new IllegalArgumentException("A user with this email or username already exists: " + email);
            }
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Keycloak user creation failed with status " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            realm().users().get(userId).resetPassword(credential);

            log.debug("Keycloak user created: username={} id={}", username, userId);
            return userId;
        }
    }

    public void setEmailVerified(String userId, boolean verified) {
        var userResource = realm().users().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEmailVerified(verified);
        userResource.update(user);
    }

    /** Changes a user's email; the new address is unverified until confirmed again. */
    public void updateEmail(String userId, String email) {
        var userResource = realm().users().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEmail(email);
        user.setEmailVerified(false);
        userResource.update(user);
    }

    public void assignRealmRole(String userId, PlatformRole role) {
        RoleRepresentation kcRole = realm().roles().get(role.roleName()).toRepresentation();
        realm().users().get(userId).roles().realmLevel().add(List.of(kcRole));
        log.debug("Assigned realm role '{}' to user {}", role.roleName(), userId);
    }

    // ── Best-effort rollback ─────────────────────────────────────────────

    public void deleteUserQuietly(String userId) {
        if (userId == null) return;
        try {
            realm().users().get(userId).remove();
            log.warn("Rolled back Keycloak user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to rollback Keycloak user {}: {}", userId, e.getMessage());
        }
    }

    public void deleteOrganisationQuietly(String orgId) {
        if (orgId == null) return;
        try {
            realm().organizations().get(orgId).delete();
            log.warn("Rolled back Keycloak org: {}", orgId);
        } catch (Exception e) {
            log.error("Failed to rollback Keycloak org {}: {}", orgId, e.getMessage());
        }
    }
}
