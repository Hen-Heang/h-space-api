package com.henheang.securityapi.validation;

import java.util.Locale;

/**
 * Usernames are 3-30 characters of letters, digits, '.' and '_', starting and ending with a letter
 * or digit, compared case-insensitively (stored lowercase). They can never contain '@', which is
 * what lets one login field accept either a username or an email.
 */
public final class UsernameRules {

    public static final String PATTERN = "^[A-Za-z0-9][A-Za-z0-9._]{1,28}[A-Za-z0-9]$";

    public static final String MESSAGE =
            "Username must be 3-30 characters of letters, digits, '.' or '_', starting and"
                    + " ending with a letter or digit";

    private UsernameRules() {}

    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String username) {
        return username != null && username.trim().matches(PATTERN);
    }

    /** True when a login identifier should be looked up as an email rather than a username. */
    public static boolean isEmailIdentifier(String identifier) {
        return identifier != null && identifier.contains("@");
    }
}
