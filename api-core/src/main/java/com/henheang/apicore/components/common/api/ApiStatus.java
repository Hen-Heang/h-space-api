package com.henheang.apicore.components.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApiStatus {
    private int code;
    private String message;

    // Set on error responses only (GlobalExceptionHandler, RateLimitingFilter): the
    // request's X-Request-Id, so a client can show "Error ref: ..." and the matching log
    // lines can be found. Omitted when null, so success bodies are unchanged.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId;

    public ApiStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public ApiStatus(StatusCode statusCode) {
        this.code = statusCode.getCode();
        this.message = statusCode.getMessage();
    }
}
