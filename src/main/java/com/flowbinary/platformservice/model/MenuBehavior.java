package com.flowbinary.platformservice.model;

/** What activating a menu item does. */
public enum MenuBehavior {
    /** Go to the item's route. */
    NAVIGATE,
    /** Toggle the item's children; the item itself leads nowhere. */
    EXPAND,
    /** Navigate to the route and reveal the children. */
    BOTH
}
