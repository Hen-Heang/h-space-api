package com.henheang.engineeringapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.apicore.components.RequestIdFilter;
import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.ApiStatus;
import com.henheang.apicore.components.common.api.StatusCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers the security filter chain's 401 and 403 in the same {@code ApiResponse} envelope as every
 * other response, with the request id. Never echoes why a token was rejected: the reason (expired,
 * wrong issuer, bad signature) goes nowhere a caller can probe it.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHORIZED_MESSAGE = "Sign in to H-Space to use the Engineering Hub.";
    static final String FORBIDDEN_MESSAGE =
            "The Engineering Hub is owner-only and needs a session signed in with your"
                    + " authenticator code.";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        // RFC 6750: tell the client which scheme is expected.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, StatusCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    @Override
    public void handle(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, StatusCode.FORBIDDEN, FORBIDDEN_MESSAGE);
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
