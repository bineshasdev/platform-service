package com.flowbinary.platformservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Keycloak admin-client connection settings.
 * Bound from the {@code keycloak.admin.*} namespace in application.yml.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakProperties {

    /** Keycloak base URL, e.g. http://localhost:9080 */
    private String serverUrl;

    /** Realm used to authenticate the admin client (usually "master"). */
    private String adminRealm = "master";

    /** Client ID for admin authentication (usually "admin-cli"). */
    private String clientId = "admin-cli";

    /** Admin username. */
    private String username;

    /** Admin password. */
    private String password;

    /**
     * The application realm where tenant organisations and users are managed.
     * Must exist in Keycloak before the service starts (create it manually or via import).
     */
    private String appRealm;

    private String clientSecret; // Optional: if the admin client uses client-secret auth instead of username/password

    /**
     * Keycloak theme name applied to the login and password-reset UI pages.
     * Leave blank to keep the realm's current theme unchanged.
     */
    private String loginTheme;

    /**
     * Keycloak theme name applied to all outgoing emails (password-reset email, etc.).
     * Defaults to the same value as {@code loginTheme} when not set explicitly.
     */
    private String emailTheme;

    public String getResolvedEmailTheme() {
        return (emailTheme != null && !emailTheme.isBlank()) ? emailTheme : loginTheme;
    }
}
