package com.henheang.engineeringapi.exception;

/**
 * A resource the caller can't see: it doesn't exist <em>or belongs to another owner</em> - the same
 * 404 either way, so ids can't be probed.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " not found.");
    }
}
