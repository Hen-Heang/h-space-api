package com.henheang.hubapi.security;

import com.henheang.hubapi.config.HubSupabaseAuthProperties;
import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.supabaseauth.SupabaseAuthenticationConverter;
import com.henheang.supabaseauth.SupabaseSession;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The verified caller's user id for the routes the Auth V2 pilot can take over (the Inbox). Exactly
 * one kind of caller is accepted, decided by configuration - never "try one, then the other":
 *
 * <ul>
 *   <li>pilot off (default): security-api's {@link UserPrincipal}, as before;
 *   <li>pilot on: a Supabase token that passed the pilot chain - admitted member with {@code aal2}.
 *       The chain already enforces this; checking it again here keeps a future routing mistake from
 *       turning into access.
 * </ul>
 *
 * Anything else is 403. The id never comes from a body, path or query parameter.
 */
@Component
@EnableConfigurationProperties(HubSupabaseAuthProperties.class)
public class HubCaller {

    private final boolean supabasePilot;

    public HubCaller(HubSupabaseAuthProperties properties) {
        this.supabasePilot = properties.enabled();
    }

    public UUID inboxUserId(Authentication authentication) {
        if (supabasePilot) {
            if (authentication instanceof JwtAuthenticationToken token
                    && has(token, HubAuthorities.MEMBER)
                    && has(token, SupabaseAuthenticationConverter.MFA)) {
                return SupabaseSession.userId(token.getToken());
            }
            throw new AccessDeniedException("Not an admitted H-Space member with aal2");
        }
        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new AccessDeniedException("Not a signed-in user");
    }

    private static boolean has(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
