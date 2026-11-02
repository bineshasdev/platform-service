package com.flowbinary.platformservice.authz.sdk;

import com.flowbinary.platformservice.authz.model.AuthzPrivilege;
import com.flowbinary.platformservice.authz.sdk.Decision.Layer;
import com.flowbinary.platformservice.authz.service.EffectivePermissionService;
import com.flowbinary.platformservice.authz.service.PermissionSet;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.AuditService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The decision bean every protected action goes through, as
 * {@code @RequiresPrivilege("…")} or {@code authz.require(auth, "…")}.
 *
 * <p>Layer CONTEXT: a JWT with a user subject; for tenant privileges a tenant
 * pinned in the token, an active tenant and an active membership; for
 * platform-only privileges an active platform membership. Layer PRIVILEGE: the
 * effective set contains the privilege. Unknown privileges are denied.
 * Layers SCOPE to CONSENT arrive in later phases.
 */
@Slf4j
@Component("authz")
public class Authz {

    private final TenantContextResolver tenantContext;
    private final EffectivePermissionService permissions;
    private final PermissionCache cache;
    private final AuditService audit;
    private final Cache<String, Map<String, AuthzPrivilege>> catalogue = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(1).build();

    public Authz(TenantContextResolver tenantContext, EffectivePermissionService permissions,
                 PermissionCache cache, AuditService audit) {
        this.tenantContext = tenantContext;
        this.permissions = permissions;
        this.cache = cache;
        this.audit = audit;
    }

    /** Forget the privilege catalogue so a privilege a service has just declared is known at once. */
    public void refreshCatalogue() {
        catalogue.invalidateAll();
    }

    /** For {@code @PreAuthorize}: true when every implemented layer allows. */
    public boolean has(Authentication authentication, String privilege) {
        return decide(authentication, privilege).allowed();
    }

    /** Throws {@link AccessDeniedException} naming the refusing layer. */
    public void require(Authentication authentication, String privilege) {
        Decision d = decide(authentication, privilege);
        if (!d.allowed()) throw new AccessDeniedException("Denied at " + d.layer() + ": " + d.reason());
    }

    /**
     * True for an active member of the tenant pinned in the token. The check for endpoints every
     * member may use (their own profile, notifications, the menu, files).
     */
    public boolean member(Authentication authentication) {
        Optional<Jwt> jwt = jwt(authentication);
        if (jwt.isEmpty()) return false;
        Optional<UUID> sub = subject(jwt.get());
        Optional<Tenant> tenant = tenantContext.resolveFromJwt(jwt.get());
        if (sub.isEmpty() || tenant.isEmpty()) return false;
        UUID tenantId = tenant.get().getId();
        PermissionSet set = cache.get(tenantId, sub.get(), () -> permissions.forTenant(tenantId, sub.get()));
        boolean active = set.status() == PermissionSet.Status.ACTIVE;
        if (!active) log.info("Authz denied: member check, status={}", set.status());
        return active;
    }

    /**
     * Holds {@code privilege} in the caller's own tenant, and {@code tenantId} (from a request path) is
     * that tenant. A path naming another tenant is refused whatever the caller holds.
     */
    public boolean hasInTenant(Authentication authentication, String privilege, UUID tenantId) {
        Optional<Jwt> jwt = jwt(authentication);
        Optional<UUID> own = jwt.flatMap(tenantContext::resolveFromJwt).map(Tenant::getId);
        if (own.isEmpty() || !own.get().equals(tenantId)) {
            log.info("Authz denied: path tenant {} is not the caller's tenant", tenantId);
            return false;
        }
        return has(authentication, privilege);
    }

    public Decision decide(Authentication authentication, String privilege) {
        Decision d = evaluate(authentication, privilege);
        if (!d.allowed()) recordDenial(authentication, d);
        return d;
    }

    /**
     * The caller's permission set in the tenant pinned in their token, through
     * the cache. Empty with a non-ACTIVE status when they are not a member.
     */
    public PermissionSet current(Authentication authentication) {
        Jwt jwt = jwt(authentication).orElseThrow(() -> new AccessDeniedException("Not a user token"));
        UUID sub = subject(jwt).orElseThrow(() -> new AccessDeniedException("Token has no user subject"));
        Tenant tenant = tenantContext.resolveFromJwt(jwt)
                .orElseThrow(() -> new AccessDeniedException("No tenant in token"));
        return cache.get(tenant.getId(), sub, () -> permissions.forTenant(tenant.getId(), sub));
    }

    /** The caller's platform (operator) permission set, through the cache. */
    public PermissionSet currentPlatform(Authentication authentication) {
        Jwt jwt = jwt(authentication).orElseThrow(() -> new AccessDeniedException("Not a user token"));
        UUID sub = subject(jwt).orElseThrow(() -> new AccessDeniedException("Token has no user subject"));
        return cache.get(null, sub, () -> permissions.forPlatform(sub));
    }

    private Decision evaluate(Authentication authentication, String privilege) {
        AuthzPrivilege meta = catalogue.get("all", k -> permissions.catalogue()).get(privilege);
        if (meta == null) return Decision.deny(Layer.CONTEXT, privilege, "unknown privilege");

        Optional<Jwt> jwt = jwt(authentication);
        if (jwt.isEmpty()) return Decision.deny(Layer.CONTEXT, privilege, "not a JWT authentication");
        Optional<UUID> sub = subject(jwt.get());
        if (sub.isEmpty()) return Decision.deny(Layer.CONTEXT, privilege, "token has no user subject");

        PermissionSet set;
        if (meta.isAssignableByTenant()) {
            Optional<Tenant> tenant = tenantContext.resolveFromJwt(jwt.get());
            if (tenant.isEmpty()) return Decision.deny(Layer.CONTEXT, privilege, "no tenant in token");
            UUID tenantId = tenant.get().getId();
            set = cache.get(tenantId, sub.get(), () -> permissions.forTenant(tenantId, sub.get()));
        } else {
            set = cache.get(null, sub.get(), () -> permissions.forPlatform(sub.get()));
        }

        if (set.status() != PermissionSet.Status.ACTIVE) {
            return Decision.deny(Layer.CONTEXT, privilege, set.status().name().toLowerCase());
        }
        if (!set.privileges().contains(privilege)) {
            return Decision.deny(Layer.PRIVILEGE, privilege, "privilege not held");
        }
        return Decision.allow(privilege);
    }

    private void recordDenial(Authentication authentication, Decision d) {
        log.info("Authz denied: privilege={} layer={} reason={}", d.privilege(), d.layer(), d.reason());
        try {
            UUID actor = jwt(authentication).flatMap(Authz::subject).orElse(null);
            UUID tenantId = jwt(authentication).flatMap(tenantContext::resolveFromJwt).map(Tenant::getId).orElse(null);
            audit.record(new AuditService.Entry(tenantId, actor, "AUTHZ_DENIED", "privilege", d.privilege(),
                    null, null, d.reason(), "API", null, Map.of("layer", d.layer().name())));
        } catch (RuntimeException e) {
            log.warn("Could not audit authz denial: {}", e.getMessage());
        }
    }

    private static Optional<Jwt> jwt(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken t ? Optional.of(t.getToken()) : Optional.empty();
    }

    private static Optional<UUID> subject(Jwt jwt) {
        try {
            return Optional.ofNullable(jwt.getSubject()).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
