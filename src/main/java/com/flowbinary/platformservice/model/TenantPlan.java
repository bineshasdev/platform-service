package com.flowbinary.platformservice.model;

/**
 * Subscription plan tier. Values are lowercase to match the database CHECK constraint.
 */
public enum TenantPlan {
    free, starter, pro, enterprise
}
