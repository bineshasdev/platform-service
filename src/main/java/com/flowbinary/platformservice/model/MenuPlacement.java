package com.flowbinary.platformservice.model;

/**
 * Where a menu item renders in its application. One tree per application;
 * a UI picks the subtrees of the placement it is drawing.
 */
public enum MenuPlacement {
    /** Left (primary) navigation. */
    LEFT,
    /** Top bar controls. */
    HEADER,
    /** The "+ New" quick-create menu. */
    QUICK,
    /** The avatar / account menu. */
    USER,
    /** A page of the settings hub. */
    SETTINGS,
    /** A tab inside a screen. */
    TAB,
    /** A reachable route that is in no menu (detail pages, wizards). */
    INNER,
    /** A home tile (kiosk, portal). */
    TILE
}
