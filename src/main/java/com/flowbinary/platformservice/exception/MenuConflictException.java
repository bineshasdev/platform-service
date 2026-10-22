package com.flowbinary.platformservice.exception;

/** The draft or scope changed underneath the caller; they must reload and retry. */
public class MenuConflictException extends RuntimeException {
    public MenuConflictException(String message) {
        super(message);
    }
}
