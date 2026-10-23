package com.henheang.commonapi.components.common.api;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum StatusCode {

    // Success 200
    SUCCESS(200, "Success", 200),

    // 401 Unauthorized
    UNAUTHORIZED(40100, "Unauthorized", 401),

    // 403 Forbidden
    FORBIDDEN(40300, "Forbidden", 403),

    // 400 Bad Request & Validation Errors
    BAD_REQUEST(40000, "Bad Request", 400),
    PHONE_NUMBER_MUST_BE_ENCRYPT(40001, "Phone number must be encrypted", 400),
    PHONE_NUMBER_INVALID(40002, "Phone number is invalid", 400),
    DISABLE_SENT_OTP_CODE_15_MIN(40003, "Disabled send OTP code for 15 minutes", 400),
    DISABLE_SENT_OTP_CODE_5_MIN(40004, "Disabled send OTP code for 5 minutes", 400),
    SECURITY_CODE_MUST_BE_ENCRYPTED(40005, "Security code must be encrypted", 400),
    SECURITY_KEY_MUST_BE_ENCRYPTED(40006, "Security key must be encrypted", 400),
    SECURITY_CODE_EXPIRED(40007, "Security code is expired", 400),
    DISABLE_VERIFY_OTP_CODE(40008, "Verify OTP code is disabled", 400),
    SECURITY_CODE_INCORRECT(40009, "Security code is incorrect", 400),
    PASSWORD_MUST_BE_ENCRYPTED(40010, "Password must be encrypted", 400),
    WALLET_ALREADY_EXISTS(40011, "Wallet already exists", 400),
    WALLET_ALREADY_LINKED(40012, "Wallet already linked", 400),
    DISABLE_SENT_OTP_CODE(40013, "Disabled send OTP code for 15 minutes.", 400),
    DISABLE_SENT_OTP_CODE_FOR_5_MINUTES(40014, "Disabled send OTP code for 5 minutes.", 400),
    WALLET_ID_MUST_BE_ENCRYPTED(40015, "Wallet ID must be encrypted", 400),
    WALLET_ID_ALREADY_LINKED(40016, "Wallet ID already linked", 400),
    PHONE_NUMBER_EMAIL_NOT_MATCH(40017, "Phone number and email do not match", 400),
    WALLET_ALREADY_LINKED_TO_ANOTHER_USER(40018, "Wallet is already linked to another user", 400),
    HASH_IS_INCORRECT(40019, "Hash is incorrect", 400),

    // 404 Not Found
    NOT_FOUND(40400, "Not Found", 404),
    PACKAGE_NOT_FOUND(40401, "Package is not found", 404),
    PRODUCT_USER_NOT_FOUND(40402, "Product user is not found", 404),
    TOP_UP_TRANSACTION_NOT_FOUND(40403, "Top up transaction is not found", 404),
    ACCOUNT_NO_NOT_FOUND(40404, "Account number is not found", 404),
    SECURITY_KEY_NOT_FOUND(40405, "Security key was not found", 404),
    SECURITY_CODE_NOT_FOUND(40406, "Security code was not found", 404),
    PRODUCT_NOT_FOUND(40407, "Product not found", 404),
    WALLET_NOT_FOUND(40408, "Wallet not found", 404),
    PRODUCT_POLICY_NOT_FOUND(40409, "Product policy not found", 404),
    USER_ACCOUNT_DISABLED(40410, "User account is disabled", 404),
    USER_ACCOUNT_REMOVED(40411, "User account is removed", 404),
    CLIENT_ID_NOT_FOUND(40412, "Client id was not found", 404),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(50000, "Internal Server Error", 500),
    POINT_PROCESSING_ERROR(50001, "Point processing error", 500),
    INSUFFICIENT_POINTS(50002, "Insufficient points", 500),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(42900, "Too many requests, please try again later", 429),

    // 503 Service Unavailable
    SEND_OTP_FAILED(50300, "Send OTP failed", 503);

    private final String message;
    private final int code;
    private final int httpCode;

    StatusCode(final int code, final String message, int httpCode) {
        this.message = message;
        this.code = code;
        this.httpCode = httpCode;
    }

    // Runtime duplicate checker
    static {
        Set<Integer> seenCodes = new HashSet<>();
        for (StatusCode status : StatusCode.values()) {
            if (!seenCodes.add(status.code)) {
                throw new IllegalStateException("Duplicate StatusCode code: " + status.code);
            }
        }
    }
}
