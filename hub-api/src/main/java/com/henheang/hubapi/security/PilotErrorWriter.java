package com.henheang.hubapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.apicore.components.RequestIdFilter;
import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.ApiStatus;
import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.supabaseauth.SupabaseAuthenticationConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The pilot chain's 401 / 403 / 503, in the same {@code ApiResponse} envelope (with the request id)
 * as every other response. Never says why a token was rejected; the reason (expired, wrong issuer,
 * bad signature) goes nowhere a caller can probe it. Not a bean: the pilot config makes one, so
 * legacy security-api code never picks it up.
 */
public class PilotErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHORIZED_MESSAGE = "Sign in to H-Space to continue.";
    static final String NOT_ADMITTED_MESSAGE = "This account doesn't have access to H-Space.";
    static final String MFA_REQUIRED_MESSAGE =
            "This needs a session verified with your authenticator code.";
    static final String UNAVAILABLE_MESSAGE =
            "H-Space couldn't confirm your access right now. Try again in a moment.";

    private final ObjectMapper objectMapper;

    public PilotErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, StatusCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    /**
     * 403 from the authorization rules. An admitted member without {@code aal2} is told it's the
     * second factor (they are the account's owner, so this reveals nothing); everyone else gets the
     * generic answer.
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean memberWithoutMfa =
                has(auth, HubAuthorities.MEMBER) && !has(auth, SupabaseAuthenticationConverter.MFA);
        write(
                response,
                StatusCode.FORBIDDEN,
                memberWithoutMfa ? MFA_REQUIRED_MESSAGE : NOT_ADMITTED_MESSAGE);
    }

    void notAdmitted(HttpServletResponse response) throws IOException {
        write(response, StatusCode.FORBIDDEN, NOT_ADMITTED_MESSAGE);
    }

    void unavailable(HttpServletResponse response) throws IOException {
        write(response, StatusCode.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
    }

    private static boolean has(Authentication auth, String authority) {
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private void write(HttpServletResponse response, StatusCode code, String message)
            throws IOException {
        ApiResponse<Object> body =
                new ApiResponse<>(new ApiStatus(code.getCode(), message), message);
        body.getStatusCode().setRequestId(RequestIdFilter.current());
        response.setStatus(code.getHttpCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
