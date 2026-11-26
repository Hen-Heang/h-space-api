package com.henheang.securityapi.exception;

import com.henheang.apicore.components.common.api.StatusCode;
import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {
    private final StatusCode statusCode;

    public AuthException(StatusCode statusCode) {
        super(statusCode.getMessage());
        this.statusCode = statusCode;
    }

    public AuthException(StatusCode statusCode, String customMessage) {
        super(customMessage);
        this.statusCode = statusCode;
    }
}
