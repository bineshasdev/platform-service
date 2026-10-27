package com.flowbinary.platformservice.model;

public enum ModuleStatus {
    /** Registered, waiting for an operator's approval; may not push. */
    PENDING,
    /** May push (and be pulled); its items are live. */
    ACTIVE,
    /** Items hidden, manifests refused; reversible. */
    SUSPENDED,
    /** Items removed; the code stays reserved. */
    DEREGISTERED
}
