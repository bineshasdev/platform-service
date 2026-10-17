package com.flowbinary.platformservice.model;

/**
 * Platform-level Keycloak realm roles.
 * These are seeded into the app realm on startup by {@code KeycloakRoleInitializer}.
 *
 * <p>The {@code roleName()} value is the exact string stored as the Keycloak realm
 * role name and persisted in {@code users.role} (via {@link PlatformRoleConverter}).
 * {@code SecurityConfig} maps each to a {@code ROLE_<UPPERCASE>} granted authority.
 *
 * <ul>
 *   <li>{@link #SUPERADMIN}         — full platform access (CliniqAI operators)</li>
 *   <li>{@link #TENANTADMIN}        — owns a tenant and its users</li>
 *   <li>{@link #CLINIC_ADMIN}       — manages a clinic tenant's staff, roles, schedules</li>
 *   <li>{@link #DOCTOR} / {@link #PATIENT} — portable, platform-wide identities (not tenant-scoped)</li>
 *   <li>healthcare staff roles      — one per provider-side persona (PR-004 / FR-022)</li>
 *   <li>{@link #USER} / {@link #GUEST} — generic authenticated / read-only fallback</li>
 * </ul>
 */
public enum PlatformRole {

    // ── Generic platform roles ────────────────────────────────────────────
    SUPERADMIN("superadmin"),
    TENANTADMIN("tenantadmin"),

    // ── Platform-wide identities — not tenant-scoped ──────────────────────
    /** Platform-wide practitioner identity. */
    DOCTOR("doctor"),
    /** Platform-wide patient identity. */
    PATIENT("patient"),

    // ── CliniqAI healthcare built-in staff roles ─────────────────────────
    CLINIC_ADMIN("clinic_admin"),
    NURSE("nurse"),
    RECEPTIONIST("receptionist"),
    LAB_ADMIN("lab_admin"),
    LAB_TECHNICIAN("lab_technician"),
    PHLEBOTOMIST("phlebotomist"),
    PHARMACIST("pharmacist"),
    MEDICAL_CODER("medical_coder"),
    BILLING_STAFF("billing_staff"),
    COMPLIANCE_OFFICER("compliance_officer"),

    // ── Generic fallback roles ───────────────────────────────────────────
    USER("user"),
    GUEST("guest");

    private final String roleName;

    PlatformRole(String roleName) {
        this.roleName = roleName;
    }

    /** The exact string stored as the Keycloak realm role name. */
    public String roleName() {
        return roleName;
    }
}
