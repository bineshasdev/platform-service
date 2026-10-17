package com.flowbinary.platformservice.model;

/**
 * The codes of the built-in tenant roles.
 *
 * <p><strong>This enum is no longer the runtime role model.</strong> Since V15
 * roles live in the {@code roles} table ({@link Role}), so that clinic admins can
 * compose custom roles from the privilege catalogue (PR-004 / FR-022). These
 * constants are the vocabulary those system rows were seeded with — each value
 * matches a {@code roles.code} where {@code tenant_id IS NULL} — and are kept as
 * type-safe references for code that needs to name a built-in role.
 *
 * <p>The first five are the generic platform roles inherited from FlowCore; the
 * remainder are the CliniqAI healthcare built-in roles, one per provider-side
 * persona.
 */
public enum UserRole {

    // ── Generic platform roles ────────────────────────────────────────────
    OWNER,
    ADMIN,
    MANAGER,
    USER,
    VIEWER,

    // ── CliniqAI healthcare built-in roles ────────────────────────────────
    /** Runs a clinic tenant: staff, roles, locations, schedules, verification. */
    CLINIC_ADMIN,
    /** Licensed practitioner — charting, prescribing, ordering. */
    DOCTOR,
    /** Records vitals, assists encounters, manages monitored chronic patients. */
    NURSE,
    /** Front desk — registration, booking, queue, payments. */
    RECEPTIONIST,
    /** Runs a lab tenant: test catalog, pricing, TAT, sample tracking. */
    LAB_ADMIN,
    /** Processes samples and uploads results. */
    LAB_TECHNICIAN,
    /** Home sample collection. */
    PHLEBOTOMIST,
    /** Runs a pharmacy tenant: e-prescription queue, dispensing, substitutions. */
    PHARMACIST,
    /** Attaches diagnostic / procedure codes to encounters. */
    MEDICAL_CODER,
    /** Charge capture, invoicing, collections, claims, day-close. */
    BILLING_STAFF,
    /** DPO — access anomalies, consent violations, credential expiry, breach workflow. */
    COMPLIANCE_OFFICER,
    /** The person receiving care — portal/mobile self-service, not a tenant staff member. */
    PATIENT
}
