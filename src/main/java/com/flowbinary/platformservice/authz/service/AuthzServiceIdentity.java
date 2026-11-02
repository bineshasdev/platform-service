package com.flowbinary.platformservice.authz.service;

import com.flowbinary.platformservice.model.ModuleStatus;
import com.flowbinary.platformservice.repository.MenuModuleRepository;
import com.flowbinary.platformservice.security.ModuleIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Proves a caller is the service whose privileges it wants to declare: a client-credentials token whose
 * client owns the active module registered for that service name (design §21.1, P4-2). An unknown service
 * answers exactly like a wrong client, so the endpoint cannot be used to discover which services exist.
 */
@Component
@RequiredArgsConstructor
public class AuthzServiceIdentity {

    private static final String DENIED = "Not permitted";

    private final MenuModuleRepository modules;

    /** @return the authenticated client id */
    public String requireOwner(Authentication authentication, String service) {
        if (!(authentication instanceof JwtAuthenticationToken token)) throw new AccessDeniedException(DENIED);
        String client = ModuleIdentity.serviceClientId(token.getToken());
        if (client == null) throw new AccessDeniedException(DENIED);
        boolean owns = modules.findLiveByPrivilegeModuleCode(service).stream()
                .anyMatch(m -> m.getStatus() == ModuleStatus.ACTIVE && client.equals(m.getOwnerClientId()));
        if (!owns) throw new AccessDeniedException(DENIED);
        return client;
    }
}
