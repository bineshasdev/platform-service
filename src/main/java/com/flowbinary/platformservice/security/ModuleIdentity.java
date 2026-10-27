package com.flowbinary.platformservice.security;

import com.flowbinary.platformservice.model.MenuModule;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Proves that a caller is the module it claims to be: a Keycloak
 * <em>client-credentials</em> token whose authorized party is exactly the
 * client registered as that module's owner.
 *
 * <p>A user token that happens to carry the same {@code azp} (authorization-code
 * flow) is refused, and an unknown module code gives the same answer as a wrong
 * client, so the endpoint cannot be used to discover which modules exist.
 */
@Component
@RequiredArgsConstructor
public class ModuleIdentity {

    private static final String DENIED = "Not permitted";

    private final MenuModuleRepository modules;

    /** @return the authenticated client id; throws {@link AccessDeniedException} unless it owns {@code moduleCode} */
    public String requireOwner(Authentication authentication, String moduleCode) {
        if (!(authentication instanceof JwtAuthenticationToken token)) throw new AccessDeniedException(DENIED);
        String client = serviceClientId(token.getToken());
        if (client == null) throw new AccessDeniedException(DENIED);

        MenuModule module = modules.findByCode(moduleCode).orElse(null);
        if (module == null || !module.getOwnerClientId().equals(client)) throw new AccessDeniedException(DENIED);
        return client;
    }

    /** The client id of a client-credentials token, or null for anything else. */
    public static String serviceClientId(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if (azp == null || azp.isBlank()) return null;
        String username = jwt.getClaimAsString("preferred_username");
        boolean serviceAccount = (username != null && username.startsWith("service-account-"))
                || azp.equals(jwt.getClaimAsString("clientId"));
        return serviceAccount ? azp : null;
    }
}
