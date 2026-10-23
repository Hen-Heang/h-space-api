package com.example.jwt_authentication.common.api;

import lombok.Getter;

@Getter
public enum StatusCode {

    SUCCESS(200, "Success"),
    BAD_GATEWAY(502, "Connection timeout"),
    BAD_REQUEST(400, "Bad request"),
    // 401 Unauthorized
    UNAUTHORIZED(401, "Unauthorized"),

    // 404 Not Found
    NOT_FOUND(404, "Not Found"),

    ;

    private final String message;
    private final int code;

    StatusCode(final int code, final String message) {

        this.message = message;
        this.code = code;
    }

}