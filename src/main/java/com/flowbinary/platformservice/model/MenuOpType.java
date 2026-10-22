package com.flowbinary.platformservice.model;

/** An operation recorded in a menu draft. */
public enum MenuOpType {
    /** Create an owned item, or replace its full state. */
    PUT_ITEM,
    /** Change some fields of an owned item; null fields stay as they are. */
    PATCH_ITEM,
    MOVE_ITEM,
    /** Set the order of the listed siblings. */
    REORDER,
    /** Enable or disable an item (tenant scope: hide or show a global item). */
    SET_ENABLED,
    DELETE_ITEM,
    /** Replace the roles that grant an item. Needs {@code menu.assign}. */
    SET_ROLES,
    /** Grant, deny or clear a single user's access. Needs {@code menu.assign}. */
    SET_USER_ACCESS,
    /** Tenant scope only: relabel / re-icon / reorder / hide a global item. */
    SET_OVERRIDE;

    /** Assignment operations need {@code menu.assign}; the rest {@code menu.manage}. */
    public boolean isAssignment() {
        return this == SET_ROLES || this == SET_USER_ACCESS;
    }
}
