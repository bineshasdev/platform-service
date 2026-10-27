package com.flowbinary.platformservice.model;

public enum MenuAudienceMode {
    /** Visible to everyone who passes the mandatory plan, feature and privilege gates. */
    ALL_ELIGIBLE,
    /** Visible only to holders of a role / user / group grant on the item. */
    RESTRICTED
}
