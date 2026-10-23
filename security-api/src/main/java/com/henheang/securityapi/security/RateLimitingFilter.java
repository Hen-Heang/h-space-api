package com.henheang.securityapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.commonapi.components.common.api.ApiResponse;
import com.henheang.commonapi.components.common.api.StatusCode;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps requests per client IP on brute-forceable auth endpoints (login, signup, forgot-password).
 * In-memory only - fine for a single instance; a multi-instance deployment would need a shared
 * store (e.g. Redis) instead.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/forgot-password",
                    "/api/auth/mfa/setup",
                    "/api/auth/mfa/enable",
                    "/api/auth/mfa/disable",
                    "/api/auth/mfa/verify",
                    "/api/auth/mfa/backup-codes/regenerate");
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
        if (!LIMITED_PATHS.contains(path)) {
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
        objectMapper.writeValue(response.getWriter(), body);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
