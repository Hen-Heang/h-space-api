package com.henheang.apicore.components;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id so its log lines can be found together and a client can
 * quote it when reporting a problem.
 *
 * <p>The id comes from the caller's {@code X-Request-Id} header when it is a short, plain token
 * (letters, digits, {@code . _ -}, at most 64 chars); otherwise a fresh UUID is used. The header is
 * untrusted input, so anything else is replaced rather than logged - that keeps log injection
 * (newlines, fake log lines) and oversized values out. It is a correlation id only: never use it
 * for authentication, idempotency or anything security-relevant, and never put it into SQL.
 *
 * <p>The id is put in the SLF4J MDC under {@value #MDC_KEY} for the duration of the request (the
 * log pattern prints it) and echoed back in the {@value #HEADER} response header. It is set before
 * the rest of the chain runs, so responses written by Spring Security (401/403) carry it too.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled: a leftover value would tag the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
