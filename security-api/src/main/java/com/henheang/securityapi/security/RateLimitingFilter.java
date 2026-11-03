package com.henheang.securityapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.apicore.components.RequestIdFilter;
import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.StatusCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps requests per client IP on brute-forceable or email-sending auth endpoints (login, signup,
 * forgot-password, resends, ...). In-memory only - fine for a single instance; a multi-instance
 * deployment would need a shared store (e.g. Redis) instead.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/forgot-password",
                    "/api/auth/resend-verification",
                    "/api/auth/resend-unlock-link",
                    "/api/auth/oauth2/google",
                    "/api/auth/email-code/request",
                    "/api/auth/email-code/verify",
                    "/api/auth/mfa/setup",
                    "/api/auth/mfa/enable",
                    "/api/auth/mfa/disable",
                    "/api/auth/mfa/verify",
                    "/api/auth/mfa/backup-codes/regenerate");
    // Limited on writes only: reading the keyset happens on every vault unlock,
    // but replacing it or wiping the vault should never happen in bursts.
    private static final Set<String> LIMITED_WRITE_PATHS =
            Set.of("/api/hub/vault/keyset", "/api/hub/vault/reset");
    private static final int CAPACITY = 5;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean limited =
                LIMITED_PATHS.contains(path)
                        || (LIMITED_WRITE_PATHS.contains(path) && !isRead(request));
        if (!limited) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket =
                buckets.computeIfAbsent(clientKey(request) + ":" + path, key -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(StatusCode.TOO_MANY_REQUESTS.getHttpCode());
        response.setContentType("application/json");
        ApiResponse<?> body = ApiResponse.builder().status(StatusCode.TOO_MANY_REQUESTS).build();
        // Written here, not by GlobalExceptionHandler, so add the request id the same way.
        body.getStatusCode().setRequestId(RequestIdFilter.current());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static boolean isRead(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }

    // Never read X-Forwarded-For here: its first entry is whatever the client
    // sent, so trusting it lets one caller rotate through unlimited buckets.
    // Behind a reverse proxy, Tomcat's RemoteIpValve (server.forward-headers-
    // strategy: native) already resolves the real client into getRemoteAddr(),
    // and only when the request came from a trusted proxy.
    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
