package com.henheang.engineeringapi.exception;

/** A request that passed bean validation but breaks a rule only the service can check. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
