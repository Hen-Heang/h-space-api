package com.henheang.engineeringapi.security;

import com.henheang.supabaseauth.SupabaseSession;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a verified Supabase token into an authentication that is the owner - or isn't. The {@value
 * #OWNER} authority needs both:
 *
 * <ul>
 *   <li>the token's {@code sub} in {@code engineering.auth.owner-ids}, and
 *   <li>{@code aal} = {@code aal2}: the session entered an authenticator code. A password-only
 *       session, even the owner's, gets nothing.
 * </ul>
 *
 * Any other valid token authenticates (so the caller sees 403, not 401) but holds no authority. The
 * claim reading is {@code supabase-auth}'s {@link SupabaseSession}; only the owner allowlist is
 * engineering-api's own rule.
 */
public final class OwnerAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String OWNER = "ENGINEERING_OWNER";

    private final Set<UUID> ownerIds;

    public OwnerAuthenticationConverter(Set<UUID> ownerIds) {
        this.ownerIds = Set.copyOf(ownerIds);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        boolean owner =
                SupabaseSession.tryUserId(jwt).filter(ownerIds::contains).isPresent()
                        && SupabaseSession.hasMfa(jwt);
        List<GrantedAuthority> authorities =
                owner ? List.of(new SimpleGrantedAuthority(OWNER)) : List.of();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
