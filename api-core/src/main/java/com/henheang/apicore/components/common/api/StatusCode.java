package com.henheang.apicore.components.common.api;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum StatusCode {

    // Success 200
    SUCCESS(200, "Success", 200),

    // ---- Auth codes (formerly the separate ExitCode enum). These numeric
    // codes reach the web client in status.code: keep them unchanged.

    // Authentication (1000-1099) -> 401
    AUTHENTICATION_FAILED(1000, "Authentication failed", 401),
    INVALID_CREDENTIALS(1001, "Invalid username or password", 401),
    ACCOUNT_LOCKED(1002, "Account has been locked", 401),
    ACCOUNT_DISABLED(1003, "Account is disabled", 401),
    TOKEN_EXPIRED(1004, "Authentication token has expired", 401),
    TOKEN_INVALID(1005, "Invalid authentication token", 401),
    INSUFFICIENT_PERMISSIONS(1006, "Insufficient permissions for this operation", 401),
    MFA_REQUIRED(1007, "Multi-factor authentication code required", 401),
    MFA_CODE_INVALID(1008, "Invalid multi-factor authentication code", 401),
    MFA_ALREADY_ENABLED(1009, "Multi-factor authentication is already enabled", 401),
    MFA_NOT_ENABLED(1010, "Multi-factor authentication is not enabled", 401),
    MFA_SETUP_REQUIRED(1011, "Multi-factor authentication setup must be started first", 401),
    EMAIL_LOGIN_CODE_INVALID(1012, "Invalid or expired sign-in code", 401),

    // Registration (1100-1199) -> 400
    REGISTRATION_FAILED(1100, "Registration failed", 400),
    EMAIL_ALREADY_EXISTS(1101, "Email already exists", 400),
    USERNAME_ALREADY_EXISTS(1102, "Username already exists", 400),
    PASSWORD_TOO_WEAK(1103, "Password does not meet strength requirements", 400),
    SIGNUP_INVITE_REQUIRED(1104, "Sign-up is by invitation only", 400),

    // Password reset (1200-1299) -> 400
    PASSWORD_RESET_FAILED(1200, "Password reset failed", 400),
    PASSWORD_RESET_TOKEN_EXPIRED(1201, "Password reset token has expired", 400),
    PASSWORD_RESET_TOKEN_INVALID(1202, "Invalid password reset token", 400),

    // OAuth (1300-1399) -> 400
    OAUTH_ERROR(1300, "OAuth authentication error", 400),
    OAUTH_PROVIDER_NOT_SUPPORTED(1301, "OAuth provider not supported", 400),
    OAUTH_EMAIL_NOT_VERIFIED(1302, "Email not verified by OAuth provider", 400),

    // Email verification (1400-1499) -> 400
    EMAIL_VERIFICATION_FAILED(1400, "Email verification failed", 400),
    EMAIL_VERIFICATION_TOKEN_EXPIRED(1401, "Email verification token has expired", 400),
    EMAIL_VERIFICATION_TOKEN_INVALID(1402, "Invalid email verification token", 400),
    EMAIL_ALREADY_VERIFIED(1403, "Email is already verified", 400),

    // Account unlock (1500-1599) -> 400
    ACCOUNT_UNLOCK_FAILED(1500, "Account unlock failed", 400),
    ACCOUNT_UNLOCK_TOKEN_EXPIRED(1501, "Account unlock token has expired", 400),
    ACCOUNT_UNLOCK_TOKEN_INVALID(1502, "Invalid account unlock token", 400),
    ACCOUNT_NOT_LOCKED(1503, "Account is not locked", 400),

    // System (5000-5999) -> 500
    SYSTEM_ERROR(5000, "System error", 500),
    DATABASE_ERROR(5001, "Database error", 500),
    SECURITY_ERROR(5002, "Security configuration error", 500),
    JWT_CONFIGURATION_ERROR(5003, "JWT configuration error", 500),

    // General (9000-9999) -> 500
    UNKNOWN_ERROR(9999, "Unknown error", 500),

    // ---- HTTP-shaped codes

    // 401 Unauthorized
    UNAUTHORIZED(40100, "Unauthorized", 401),

    // 403 Forbidden
    FORBIDDEN(40300, "Forbidden", 403),
    VAULT_MFA_REQUIRED(40301, "Turn on authenticator-app MFA to use the vault", 403),
    // Step-up for destructive vault actions (reset, keyset replacement).
    VAULT_MFA_CODE_REQUIRED(
            40302, "Enter a code from your authenticator app to confirm this change", 403),
    VAULT_MFA_CODE_INVALID(40303, "That code is wrong or was already used", 403),
    VAULT_MFA_LOCKED(40304, "Too many wrong codes. Try again later", 403),

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

    // 409 Conflict
    CONFLICT(40900, "Conflict", 409),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(42900, "Too many requests, please try again later", 429),

    // 502 Bad Gateway
    BAD_GATEWAY(50200, "Upstream service error", 502),

    // 503 Service Unavailable
    SEND_OTP_FAILED(50300, "Send OTP failed", 503),
    SERVICE_UNAVAILABLE(50301, "Service unavailable", 503);

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
