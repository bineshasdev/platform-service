package com.flowbinary.platformservice.model;

public enum ModuleHealth {
    /** No contact yet. */
    UNKNOWN,
    /** Last contact produced an accepted (or unchanged) manifest. */
    UP,
    /** Last contact failed or was rejected; the last accepted menu is still being served. */
    STALE,
    /** Repeated failures, or it has never delivered a valid manifest. */
    DOWN
}
