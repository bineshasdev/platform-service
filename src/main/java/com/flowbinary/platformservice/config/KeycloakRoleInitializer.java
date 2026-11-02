package com.flowbinary.platformservice.config;

import com.flowbinary.platformservice.model.PrincipalType;
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
 * Seeds the principal-type realm roles on startup, in the <b>app</b> realm.
 *
 * <p>Only the roles in {@link PrincipalType} are created: they say who someone is. What a
 * user may do comes from the authorization model, never from realm roles. Idempotent.
 */
@Slf4j
@Component
public class KeycloakRoleInitializer implements ApplicationRunner {

    private final Keycloak     keycloakAdmin;
    private final KeycloakProperties kcProps;

    public KeycloakRoleInitializer(Keycloak keycloakAdmin, KeycloakProperties kcProps) {
        this.keycloakAdmin = keycloakAdmin;
        this.kcProps = kcProps;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            RealmResource realm = keycloakAdmin.realm(kcProps.getAppRealm());

            Set<String> existing = realm.roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());

            Arrays.stream(PrincipalType.values()).forEach(principalType -> {
                String roleName = principalType.roleName();
                if (existing.contains(roleName)) {
                    log.debug("Keycloak realm role already exists: {}", roleName);
                } else {
                    RoleRepresentation role = new RoleRepresentation();
                    role.setName(roleName);
                    role.setDescription("Principal type: " + principalType.name());
                    realm.roles().create(role);
                    log.info("Created Keycloak realm role: {}", roleName);
                }
            });

        } catch (Exception ex) {
            log.error("Failed to seed Keycloak principal-type roles: {}", ex.getMessage(), ex);
        }
    }
}
