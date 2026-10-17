package com.flowbinary.platformservice.security;

import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves the current {@link Tenant} from the authenticated JWT.
 *
 * <p>Keycloak 26+ encodes organization membership in the token as:
 * <pre>
 * {
 *   "organization": {
 *     "test-org": { "id": "7e661528-4425-427e-aaef-6be76227daf7" }
 *   }
 * }
 * </pre>
 * The map key is the org alias; the {@code id} field is the Keycloak org UUID,
 * which is stored in {@link Tenant#getTenantId()}.
 */
@Component
@RequiredArgsConstructor
public class TenantContextResolver {

    private static final String ORG_CLAIM = "organization";

    private final TenantRepository tenantRepository;

    /**
     * Resolves the tenant from the currently authenticated JWT principal.
     *
     * @param authentication the Spring Security {@link Authentication} object
     * @return the matching {@link Tenant}, or empty if the claim is absent or
     *         no tenant record matches the org ID
     * @throws IllegalArgumentException if the authentication principal is not a JWT
     */
    public Optional<Tenant> resolve(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        return resolveFromJwt(jwt);
    }

    /**
     * Resolves the tenant directly from a {@link Jwt} token.
     */
    public Optional<Tenant> resolveFromJwt(Jwt jwt) {
        String orgId = extractOrgId(jwt);
        if (orgId == null) {
            return Optional.empty();
        }
        return tenantRepository.findByTenantId(orgId);
    }

    /**
     * Same as {@link #resolve(Authentication)} but throws if the tenant is not found.
     *
     * @throws TenantNotFoundException if no tenant matches the org ID in the token
     */
    public Tenant resolveRequired(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        String orgId = extractOrgId(jwt);
        if (orgId == null) {
            throw new TenantNotFoundException("JWT does not contain an 'organization' claim");
        }
        return tenantRepository.findByTenantId(orgId)
                .orElseThrow(() -> new TenantNotFoundException(
                        "No tenant found for Keycloak org ID: " + orgId));
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken();
        }
        throw new IllegalArgumentException(
                "Expected JwtAuthenticationToken but got: " +
                        (authentication == null ? "null" : authentication.getClass().getName()));
    }

    /**
     * Extracts the Keycloak org UUID from the JWT organization claim.
     *
     * <p>Token shape:
     * <pre>
     * "organization": {
     *   "test-org": { "id": "7e661528-4425-427e-aaef-6be76227daf7" }
     * }
     * </pre>
     * The key is the org alias; {@code id} inside the nested object is the UUID
     * stored in {@link com.flowbinary.platformservice.model.Tenant#getTenantId()}.
     */
    @SuppressWarnings("unchecked")
    private static String extractOrgId(Jwt jwt) {
        Object raw = jwt.getClaim(ORG_CLAIM);
        if (!(raw instanceof Map<?, ?> orgMap) || orgMap.isEmpty()) {
            return null;
        }
        // First (and normally only) entry: key = alias, value = { "id": "<uuid>" }
        Object entry = orgMap.values().iterator().next();
        if (!(entry instanceof Map<?, ?> orgDetails)) {
            return null;
        }
        return (String) ((Map<String, Object>) orgDetails).get("id");
    }
}
