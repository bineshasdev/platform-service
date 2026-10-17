package com.flowbinary.platformservice.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Applies realm-level theme settings to the Keycloak app realm at startup.
 *
 * <p>Sets {@code loginTheme} (login + password-reset UI pages) and
 * {@code emailTheme} (password-reset emails) to the values configured under
 * {@code keycloak.admin.login-theme} / {@code keycloak.admin.email-theme}.
 * No-ops when both theme values are blank (i.e. keeps whatever the realm already has).
 */
@Slf4j
public class KeycloakRealmInitializer implements ApplicationRunner {

    private  Keycloak          keycloakAdmin;
    private  KeycloakProperties kcProps;

    @Override
    public void run(ApplicationArguments args) {
        String loginTheme = kcProps.getLoginTheme();
        String emailTheme = kcProps.getResolvedEmailTheme();

        if ((loginTheme == null || loginTheme.isBlank()) &&
            (emailTheme  == null || emailTheme.isBlank())) {
            log.debug("No Keycloak theme configured — skipping realm theme initialization");
            return;
        }

        try {
            String realm = kcProps.getAppRealm();
            RealmRepresentation rep = keycloakAdmin.realm(realm).toRepresentation();

            boolean changed = false;

            if (loginTheme != null && !loginTheme.isBlank() &&
                !loginTheme.equals(rep.getLoginTheme())) {
                rep.setLoginTheme(loginTheme);
                changed = true;
            }

            if (emailTheme != null && !emailTheme.isBlank() &&
                !emailTheme.equals(rep.getEmailTheme())) {
                rep.setEmailTheme(emailTheme);
                changed = true;
            }

            if (changed) {
                keycloakAdmin.realm(realm).update(rep);
                log.info("Keycloak realm '{}' themes applied — loginTheme={} emailTheme={}",
                        realm, rep.getLoginTheme(), rep.getEmailTheme());
            } else {
                log.debug("Keycloak realm '{}' themes already up-to-date", realm);
            }

        } catch (Exception ex) {
            // Theme configuration is non-critical — log and continue rather than failing startup
            log.warn("Could not apply Keycloak realm themes: {}", ex.getMessage(), ex);
        }
    }
}
