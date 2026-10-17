package com.flowbinary.platformservice.model;

/**
 * Review state of a single {@link TenantCredential}.
 */
public enum CredentialStatus {

    /** Captured by the tenant, not yet picked up for review. */
    SUBMITTED,

    /** A platform reviewer is actively checking this credential. */
    UNDER_REVIEW,

    /** Credential confirmed valid. */
    VERIFIED,

    /** Credential rejected — see {@code reviewNotes}. */
    REJECTED,

    /** Previously verified but the document's validity period has lapsed. */
    EXPIRED
}
