package com.flowbinary.platformservice.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces a singleton {@link Keycloak} admin-client bean.
 * The client authenticates with the master realm using the admin credentials
 * defined in {@link KeycloakProperties} and is used to manage organisations,
 * users, and roles in the configured application realm.
 */
@Configuration
public class KeycloakAdminConfig {

    private final KeycloakProperties props;

    public KeycloakAdminConfig(KeycloakProperties props) {
        this.props = props;
    }

    @Bean
    Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .realm(props.getAdminRealm())
                .clientId(props.getClientId())
               // .clientSecret(props.getClientSecret())
                .username(props.getUsername())
                .password(props.getPassword())
                .build();
    }
}
