package com.flowbinary.platformservice.config;

import com.flowbinary.platformservice.model.PlatformRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds platform-level Keycloak realm roles on startup.
 *
 * <p>Roles defined in {@link PlatformRole} are created in the app realm if they
 * do not already exist. This is idempotent — re-runs are safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakRoleInitializer implements ApplicationRunner {

    private final Keycloak     keycloakAdmin;
    private final KeycloakProperties kcProps;

    @Override
    public void run(ApplicationArguments args) {
        try {
            RealmResource realm = keycloakAdmin.realm(kcProps.getAdminRealm());

            Set<String> existing = realm.roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());

            Arrays.stream(PlatformRole.values()).forEach(platformRole -> {
                String roleName = platformRole.roleName();
                if (existing.contains(roleName)) {
                    log.debug("Keycloak realm role already exists: {}", roleName);
                } else {
                    RoleRepresentation role = new RoleRepresentation();
                    role.setName(roleName);
                    role.setDescription("Platform role: " + platformRole.name());
                    realm.roles().create(role);
                    log.info("Created Keycloak realm role: {}", roleName);
                }
            });

        } catch (Exception ex) {
            log.error("Failed to seed Keycloak platform roles: {}", ex.getMessage(), ex);
        }
    }
}
