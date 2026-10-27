package com.henheang.securityapi.service;

/**
 * One-time 6-digit codes for "continue with email". Works for any address, account or not: a
 * correct code is what proves the caller owns the mailbox.
 */
public interface EmailLoginCodeService {

    /**
     * Emails a fresh code to {@code email}, replacing any outstanding one. A repeat request within
     * the resend cooldown is silently ignored, so the caller can't tell either case apart.
     */
    void sendCode(String email);

    /**
     * True, and the code is used up, when {@code code} matches the live code for {@code email}. A
     * wrong guess counts against the code's attempt limit.
     */
    boolean consumeCode(String email, String code);

    /** How {@code email} is stored and compared: trimmed and lowercased. */
    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
