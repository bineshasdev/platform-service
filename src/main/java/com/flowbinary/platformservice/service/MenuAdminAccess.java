package com.flowbinary.platformservice.service;

import com.flowbinary.platformservice.model.*;
import com.flowbinary.platformservice.repository.ApplicationRepository;
import com.flowbinary.platformservice.repository.TenantRepository;
import com.flowbinary.platformservice.repository.TenantUserRepository;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.security.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which scope an administrator may act on, and with which privileges.
 *
 * <ul>
 *   <li>The global menu needs a platform operator ({@code SUPERADMIN}).</li>
 *   <li>A tenant scope is the caller's own tenant. Only an operator may name another.</li>
 *   <li>Within a scope, each action still needs its privilege ({@code menu.manage},
 *       {@code menu.assign}, {@code menu.publish}, {@code audit.view}) — these live in
 *       the privilege catalogue, not in the token, so they are checked here.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MenuAdminAccess {

    public static final String MANAGE  = "menu.manage";
    public static final String ASSIGN  = "menu.assign";
    public static final String PUBLISH = "menu.publish";
    public static final String AUDIT   = "audit.view";

    private final TenantContextResolver     tenantContextResolver;
    private final TenantUserRepository      tenantUserRepository;
    private final TenantRepository          tenantRepository;
    private final ApplicationRepository     applicationRepository;
    private final MenuResolutionService     menuResolutionService;

    /** The acting administrator, their scope, and what they hold. */
    public record AdminContext(TenantUser actor, Tenant actorTenant, MenuScope scope, Set<String> privileges) {

        public boolean isOperator() {
            return actor.getRole() == PlatformRole.SUPERADMIN;
        }

        public boolean has(String privilege) {
            return privileges.contains("*") || privileges.contains(privilege);
        }

        public void require(String privilege) {
            if (!has(privilege)) throw new AccessDeniedException("Missing privilege " + privilege);
        }

        public UUID actorId() {
            return actor.getAuthUserId();
        }
    }

    public enum ScopeKind { GLOBAL, TENANT }

    @Transactional(readOnly = true)
    public AdminContext resolve(Authentication authentication, String applicationCode,
                                ScopeKind kind, UUID tenantIdParam) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        Tenant actorTenant = tenantContextResolver.resolveRequired(authentication);
        UUID authUserId = UUID.fromString(jwt.getSubject());
        TenantUser actor = tenantUserRepository
                .findByTenantIdAndAuthUserId(actorTenant.getId(), authUserId)
                .orElseThrow(() -> new TenantNotFoundException("User not found in tenant: " + authUserId));

        boolean operator = actor.getRole() == PlatformRole.SUPERADMIN;
        Application app = applicationRepository.findByCode(applicationCode)
                .orElseThrow(() -> new NoSuchElementException("Unknown application " + applicationCode));

        MenuScope scope;
        if (kind == ScopeKind.GLOBAL) {
            if (!operator) throw new AccessDeniedException("Only platform operators manage the global menu");
            scope = new MenuScope(app, null);
        } else {
            Tenant tenant = actorTenant;
            if (tenantIdParam != null && !tenantIdParam.equals(actorTenant.getId())) {
                // Same answer whether or not that tenant exists: no enumeration.
                if (!operator) throw new AccessDeniedException("Not permitted for that tenant");
                tenant = tenantRepository.findById(tenantIdParam)
                        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
            }
            scope = new MenuScope(app, tenant);
        }
        return new AdminContext(actor, actorTenant, scope, menuResolutionService.effectivePrivileges(actor));
    }
}
