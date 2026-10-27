package com.flowbinary.platformservice.model;

public enum MenuLifecycle {
    DRAFT, SCHEDULED, ACTIVE, DISABLED, CANCELLED, EXPIRED;

    /** States in which a temporary menu may be shown, subject to its time window. */
    public boolean isLiveCandidate() {
        return this == SCHEDULED || this == ACTIVE;
    }
}
