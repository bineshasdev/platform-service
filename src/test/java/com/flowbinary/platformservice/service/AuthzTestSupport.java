package com.flowbinary.platformservice.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Gives integration-test users a membership in the authorization model, the way
 * the legacy-role bridge does in production, so menus resolve from privileges.
 */
final class AuthzTestSupport {

    private AuthzTestSupport() {
    }

    /** Template per old role name, for a clinic tenant (the type fixtures create). */
    private static final Map<String, String> CLINIC = Map.ofEntries(
            Map.entry("tenantadmin", "clinic_admin"), Map.entry("superadmin", "clinic_admin"),
            Map.entry("clinic_admin", "clinic_admin"), Map.entry("doctor", "doctor"), Map.entry("nurse", "nurse"),
            Map.entry("receptionist", "receptionist"), Map.entry("billing_staff", "billing_staff"),
            Map.entry("medical_coder", "medical_coder"), Map.entry("compliance_officer", "compliance_officer"));

    /** A tenant's template roles, one per template of its type. Idempotent. */
    static void provision(JdbcTemplate jdbc, UUID tenantId) {
        jdbc.update("INSERT INTO authz_roles (tenant_id, template_id, kind, code, name, description) " +
                "SELECT t.id, tp.id, 'TEMPLATE', tp.code, tp.name, tp.description FROM tenants t " +
                "JOIN authz_role_templates tp ON tp.tenant_type = COALESCE(t.type, 'CLINIC') " +
                "WHERE t.id = ? AND NOT EXISTS (SELECT 1 FROM authz_roles r WHERE r.tenant_id = t.id AND r.code = tp.code)", tenantId);
    }

    /**
     * An active membership with the template for {@code role} as the primary role. The role name "superadmin"
     * also makes the user a platform operator (an active platform membership holding {@code super_admin}).
     */
    static void addMember(JdbcTemplate jdbc, UUID tenantId, UUID authId, String role) {
        String legacyRole = role;
        provision(jdbc, tenantId);
        String code = CLINIC.getOrDefault(legacyRole.toLowerCase(), "assistant");
        UUID membership = UUID.randomUUID();
        jdbc.update("INSERT INTO authz_memberships (id, tenant_id, user_sub, status) VALUES (?,?,?, 'ACTIVE')",
                membership, tenantId, authId);
        jdbc.update("INSERT INTO authz_role_assignments (membership_id, role_id, is_primary) " +
                "SELECT ?, id, true FROM authz_roles WHERE tenant_id = ? AND code = ?", membership, tenantId, code);
        if (legacyRole.equalsIgnoreCase("superadmin")) makeOperator(jdbc, authId);
    }

    /** An active platform membership holding the {@code super_admin} platform role. */
    static void makeOperator(JdbcTemplate jdbc, UUID authId) {
        UUID platform = UUID.randomUUID();
        jdbc.update("INSERT INTO authz_memberships (id, tenant_id, user_sub, status) VALUES (?, NULL, ?, 'ACTIVE')", platform, authId);
        jdbc.update("INSERT INTO authz_role_assignments (membership_id, role_id, is_primary) " +
                "SELECT ?, id, true FROM authz_roles WHERE tenant_id IS NULL AND code = 'super_admin'", platform);
    }
}
