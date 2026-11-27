package com.henheang.supabaseauth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the caller's identity and sign-in strength from a <em>verified</em> Supabase access token.
 * The user id comes only from here - never from a request body, path or query parameter.
 */
public final class SupabaseSession {

    /** Supabase's Authenticator Assurance Level claim. */
    public static final String AAL_CLAIM = "aal";

    /** The session entered a second factor (an authenticator code). */
    public static final String MFA_LEVEL = "aal2";

    private SupabaseSession() {}

    /**
     * The caller's Supabase user id ({@code sub}).
     *
     * @throws IllegalArgumentException if {@code sub} isn't a UUID. A request only reaches a
     *     controller with {@link SupabaseAuthenticationConverter#USER}, which requires one.
     */
    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** {@code sub} as a UUID, or empty when it is missing or isn't one. */
    public static Optional<UUID> tryUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    /** The token's {@code aal} claim ({@code aal1}, {@code aal2}), or empty if absent. */
    public static Optional<String> assuranceLevel(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsString(AAL_CLAIM));
    }

    /** True only when the session was signed in with a second factor ({@code aal2}). */
    public static boolean hasMfa(Jwt jwt) {
        return MFA_LEVEL.equals(jwt.getClaimAsString(AAL_CLAIM));
    }
}
