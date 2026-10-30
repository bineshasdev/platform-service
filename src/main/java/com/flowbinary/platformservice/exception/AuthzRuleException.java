package com.flowbinary.platformservice.exception;

import lombok.Getter;

/**
 * A role or membership change broke one of the authorization guardrails
 * (design §18.9). {@link #getRule()} names which one, so a UI can explain it.
 */
@Getter
public class AuthzRuleException extends RuntimeException {

    public enum Rule {
        /** The privilege does not exist for this tenant's type. */
        TENANT_TYPE,
        /** Platform-only privileges cannot be put in tenant roles. */
        PLATFORM_ONLY,
        /** The editor tried to grant or deny a privilege they do not hold. */
        ESCALATION,
        /** The tenant's plan does not include editing this kind of role. */
        ENTITLEMENT,
        /** Only TEMPLATE and DERIVED roles can be a primary role. */
        PRIMARY_KIND,
        /** Exactly one primary role per membership. */
        PRIMARY_REQUIRED,
        /** The change would leave nobody able to manage members and roles. */
        LAST_ADMIN,
        /** A role that is assigned cannot be deleted. */
        ROLE_IN_USE,
        /** Template roles are not edited; derive a role instead. */
        TEMPLATE_IMMUTABLE,
        /** No such privilege in the catalogue. */
        UNKNOWN_PRIVILEGE,
        /** A role with this code already exists in the tenant. */
        DUPLICATE_CODE
    }

    private final Rule rule;

    public AuthzRuleException(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }
}
