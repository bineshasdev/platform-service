package com.flowbinary.platformservice.model;

/**
 * Category of organisation on the platform. Drives module / menu provisioning,
 * onboarding copy, and which legal + licensing credentials a tenant must supply
 * before it can be verified and listed publicly.
 */
public enum TenantType {

    /** Clinic of any size: solo practice, polyclinic or hospital OPD. */
    CLINIC,

    /** Diagnostic, pathology or imaging laboratory. */
    LAB,

    /** Pharmacy or medical store. */
    STORE
}
