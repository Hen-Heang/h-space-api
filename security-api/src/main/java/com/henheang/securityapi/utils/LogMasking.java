package com.henheang.securityapi.utils;

// Masks personal data before it goes into an application log line. Logs are
// read by more people and tools, and kept longer, than the database, so they
// carry just enough to correlate an event ("j***@gmail.com"), never the value.
// Tokens and codes are never logged at all, masked or not - leave them out.
// Audit rows (AuditLogService) keep the full identifier; they are the
// access-controlled security record.

public final class LogMasking {

    private static final String MASK = "***";

    private LogMasking() {}

    // "jane.doe@gmail.com" -> "j***@gmail.com". Not an email -> masked like a username.
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return identifier(trimmed);
        }
        return trimmed.charAt(0) + MASK + trimmed.substring(at);
    }

    // A sign-in identifier, email or username: "janedoe" -> "j***".
    public static String identifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "<none>";
        }
        String trimmed = identifier.trim();
        if (trimmed.indexOf('@') > 0 && trimmed.indexOf('@') < trimmed.length() - 1) {
            return email(trimmed);
        }
        return trimmed.charAt(0) + MASK;
    }
}
