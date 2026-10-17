package com.flowbinary.platformservice.model;

/**
 * Lifecycle status of a tenant. Lowercase to match DB CHECK constraint.
 */
public enum TenantStatus {
    active, suspended, cancelled
}
