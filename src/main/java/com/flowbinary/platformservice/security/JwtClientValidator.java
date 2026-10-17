package com.flowbinary.platformservice.security;

import com.flowbinary.platformservice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Validates that the JWT {@code azp} (authorized party) claim belongs to a
 * trusted internal client configured under {@code app.security.allowed-clients}.
 *
 * <p>Usage in controllers:
 * <pre>
 * {@code @PreAuthorize("@jwtClientValidator.isAllowed(authentication)")}
 * </pre>
 */
@Slf4j
@Component("jwtClientValidator")
@RequiredArgsConstructor
public class JwtClientValidator {

    private static final String AZP_CLAIM = "azp";

    private final AppProperties appProperties;

    /**
     * Returns {@code true} if the token's {@code azp} claim is in the configured
     * {@code app.security.allowed-clients} list.
     */
    public boolean isAllowed(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtToken)) {
            log.warn("Access denied — not a JWT authentication: {}", authentication);
            return false;
        }

        Jwt jwt = jwtToken.getToken();
        String azp = jwt.getClaimAsString(AZP_CLAIM);

        if (azp == null) {
            log.warn("Access denied — JWT has no 'azp' claim");
            return false;
        }

        boolean allowed = appProperties.getSecurity().getAllowedClients().contains(azp);
        if (!allowed) {
            log.warn("Access denied — client '{}' is not in the allowed-clients list", azp);
        }
        return allowed;
    }
}
