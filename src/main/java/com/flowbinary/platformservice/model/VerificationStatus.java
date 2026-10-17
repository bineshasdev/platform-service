package com.flowbinary.platformservice.model;

/**
 * Verification lifecycle of a tenant's legal identity and licensing credentials.
 * A tenant is only exposed in the public directory / search once {@link #VERIFIED}.
 */
public enum VerificationStatus {

    /** Registered with minimal data; credentials not yet submitted for review. */
    PENDING,

    /** Legal profile + credentials submitted, awaiting platform review. */
    IN_REVIEW,

    /** Credentials checked and approved — tenant is publicly listable. */
    VERIFIED,

    /** Credentials rejected — tenant must correct and resubmit. */
    REJECTED
}
