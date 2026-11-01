package com.henheang.hubapi.payload.vault;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Rejects any JSON field the request type doesn't declare (the app's ObjectMapper ignores them by
 * default). For the vault, an unexpected field is far more likely a client bug about to leak
 * something - a plain {@code password}, say - than something safe to drop silently. Jackson turns
 * the exception into a 400 (GlobalExceptionHandler); the field's value is never echoed.
 */
public abstract class StrictRequest {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown field: " + name);
    }
}
