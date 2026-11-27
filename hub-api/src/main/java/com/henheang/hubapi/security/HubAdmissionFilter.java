package com.henheang.hubapi.security;

import com.henheang.supabaseauth.SupabaseAuthenticationConverter;
import com.henheang.supabaseauth.SupabaseSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Second gate of the pilot chain, after the bearer token was verified: is this Supabase user an
 * admitted H-Space member <em>right now</em>? Runs on every request (no positive cache, H13).
 *
 * <ul>
 *   <li>approved: adds {@link HubAuthorities#MEMBER} and continues;
 *   <li>no row / suspended: 403 here, before any controller, profile or database write;
 *   <li>authority unavailable: 503 - never "admitted by default".
 * </ul>
 *
 * A request without a verified Supabase user (no token, or a token whose {@code sub} isn't a UUID)
 * isn't looked up at all; the authorization rules answer it with 401 / 403. Not a bean on purpose:
 * a {@code @Component} filter would also be registered for every request in the servlet container.
 */
public class HubAdmissionFilter extends OncePerRequestFilter {

    private final AdmissionClient admissionClient;
    private final PilotErrorWriter errors;

    public HubAdmissionFilter(AdmissionClient admissionClient, PilotErrorWriter errors) {
        this.admissionClient = admissionClient;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)
                || !hasAuthority(token, SupabaseAuthenticationConverter.USER)) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID userId = SupabaseSession.userId(token.getToken());
        switch (admissionClient.check(userId, token.getToken().getTokenValue())) {
            case APPROVED -> {
                List<GrantedAuthority> authorities = new ArrayList<>(token.getAuthorities());
                authorities.add(new SimpleGrantedAuthority(HubAuthorities.MEMBER));
                JwtAuthenticationToken member =
                        new JwtAuthenticationToken(token.getToken(), authorities, token.getName());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(member);
                SecurityContextHolder.setContext(context);
                filterChain.doFilter(request, response);
            }
            case DENIED -> errors.notAdmitted(response);
            case UNAVAILABLE -> errors.unavailable(response);
        }
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
