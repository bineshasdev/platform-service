package com.flowbinary.platformservice.model;

/** Who owns a menu item's lifecycle. */
public enum MenuSource {
    /** Authored by administrators through the draft/publish workflow. */
    PLATFORM,
    /** Contributed by a registered module. */
    MODULE,
    /** Time-bound menu managed through the live temporary-menu API. */
    TEMPORARY
}
