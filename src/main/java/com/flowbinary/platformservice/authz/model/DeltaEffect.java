package com.flowbinary.platformservice.authz.model;

/** What a role delta does to a privilege. A DENY from any held role wins. */
public enum DeltaEffect {
    GRANT,
    DENY
}
