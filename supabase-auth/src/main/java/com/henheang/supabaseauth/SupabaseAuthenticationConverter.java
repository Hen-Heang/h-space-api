package com.henheang.supabaseauth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a verified Supabase access token into an authentication with two possible authorities:
 *
 * <ul>
 *   <li>{@value #USER}: {@code sub} is a UUID, i.e. a real Supabase user id;
 *   <li>{@value #MFA}: additionally, {@code aal} = {@code aal2} - the session entered an
 *       authenticator code. A password-, email-code- or Google-only session doesn't get it.
 * </ul>
 *
 * Any other valid token still authenticates, with no authority, so the caller sees 403 rather than
 * 401. Roles from the token (e.g. {@code role}) are deliberately ignored: authorization is the
 * module's job, by user id.
 */
public class SupabaseAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String USER = "SUPABASE_USER";
    public static final String MFA = "SUPABASE_MFA";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(2);
        if (SupabaseSession.tryUserId(jwt).isPresent()) {
            authorities.add(new SimpleGrantedAuthority(USER));
            if (SupabaseSession.hasMfa(jwt)) {
                authorities.add(new SimpleGrantedAuthority(MFA));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
