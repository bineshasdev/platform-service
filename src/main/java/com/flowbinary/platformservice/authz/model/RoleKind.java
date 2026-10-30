package com.flowbinary.platformservice.authz.model;

/** How a role is built. */
public enum RoleKind {
    /** A tenant's unchanged copy of a template. */
    TEMPLATE,
    /** A template plus GRANT / DENY deltas. */
    DERIVED,
    /** Privileges picked by the tenant, no template. Extra role only. */
    CUSTOM,
    /** A platform operator role (no tenant). */
    PLATFORM
}
