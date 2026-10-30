package com.flowbinary.platformservice.authz.model;

/** Only ACTIVE memberships carry privileges. */
public enum MembershipStatus {
    INVITED,
    ACTIVE,
    SUSPENDED
}
