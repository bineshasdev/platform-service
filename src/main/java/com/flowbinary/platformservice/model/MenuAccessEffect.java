package com.flowbinary.platformservice.model;

/**
 * Effect of a per-user menu assignment. {@link #DENY} always wins over any
 * grant, including one inherited from a role.
 */
public enum MenuAccessEffect {
    GRANT, DENY
}
