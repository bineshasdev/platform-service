package com.flowbinary.platformservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body returned by POST /api/v1/tenants/signup.
 */
@Getter
@Builder
public class TenantSignupResponse {

    // ── Tenant ─────────────────────────────────────────────────────

    /** Platform DB tenant ID (UUID). */
    private UUID tenantId;

    private String tenantName;
    private String displayName;
    private String slug;

    /** Keycloak Organisation ID assigned to this tenant. */
    private String keycloakOrgId;

    // ── Admin user ─────────────────────────────────────────────────

    /** Platform DB user ID (UUID). */
    private UUID adminUserId;

    /**
     * Keycloak username assigned to the admin user.
     * Pattern: {@code {slug}.admin}
     */
    private String adminUsername;

    /** Keycloak user UUID for the admin account. */
    private String keycloakUserId;

    // ── Subscription ───────────────────────────────────────────────

    private String plan;

    // ── Timestamps ─────────────────────────────────────────────────

    private Instant createdAt;
}
