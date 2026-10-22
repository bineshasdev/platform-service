package com.flowbinary.platformservice.exception;

/** A menu tree change that would break the hierarchy invariants. */
public class MenuHierarchyException extends RuntimeException {

    public enum Reason { CYCLE, MAX_DEPTH, CROSS_APPLICATION, ORPHAN, DUPLICATE_CODE, SELF_PARENT, PARENT_NOT_FOUND }

    private final Reason reason;

    public MenuHierarchyException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
