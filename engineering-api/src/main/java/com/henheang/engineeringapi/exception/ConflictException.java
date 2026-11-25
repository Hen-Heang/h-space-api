package com.henheang.engineeringapi.exception;

/** A well-formed request that clashes with existing data. The message is safe to show as-is. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
