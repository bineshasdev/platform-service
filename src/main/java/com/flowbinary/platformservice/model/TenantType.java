package com.flowbinary.platformservice.model;

/**
 * Category of organisation on the platform. Drives module / menu provisioning,
 * onboarding copy, and which legal + licensing credentials a tenant must supply
 * before it can be verified and listed publicly.
 */
public enum TenantType {

    /** Multi-practitioner clinic / polyclinic / hospital OPD. */
    CLINIC,

    /** Diagnostic, pathology or imaging laboratory. */
    LAB,

    /** Pharmacy or medical store. */
    STORE,

    /** Single practitioner running a private practice. */
    PRIVATE_CLINIC
}
