package com.henheang.engineeringapi.security;

import com.henheang.supabaseauth.SupabaseSession;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's owner id: the verified token's {@code sub}. The only place an owner id comes from -
 * never a body, path or query parameter. By the time a controller runs, {@link
 * OwnerAuthenticationConverter} has already checked {@code sub} is an allowlisted UUID.
 */
public final class CurrentOwner {

    private CurrentOwner() {}

    public static UUID id(Jwt jwt) {
        return SupabaseSession.userId(jwt);
    }
}
