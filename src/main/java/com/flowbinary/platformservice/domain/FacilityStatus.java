package com.flowbinary.platformservice.domain;

/**
 * Operational state of a physical facility (clinic / lab / store), independent of
 * the owning tenant's billing {@code status} and {@code verificationStatus}.
 */
public enum FacilityStatus {

    /** Open and operating normally. */
    ACTIVE,

    /** Set up but not currently operating (e.g. onboarding, seasonal). */
    INACTIVE,

    /** Short-term closure — renovation, staffing, local restrictions. */
    TEMPORARILY_CLOSED,

    /** Shut down for good; kept for historical records. */
    PERMANENTLY_CLOSED
}
