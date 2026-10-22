package com.flowbinary.platformservice.exception;

/** A draft operation that cannot be applied. {@code opIndex} is -1 when not tied to one operation. */
public class MenuDraftException extends RuntimeException {

    private final int opIndex;

    public MenuDraftException(int opIndex, String message) {
        super(message);
        this.opIndex = opIndex;
    }

    public int getOpIndex() {
        return opIndex;
    }
}
