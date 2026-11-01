package com.henheang.securityapi.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Shared by AuditLogServiceImpl and LoginHistoryServiceImpl, which both need
// the requesting client's IP/user-agent when writing their audit rows.
final class RequestMetadataSupport {

    private RequestMetadataSupport() {}

    static String currentClientIp() {
        HttpServletRequest request = currentRequest();
        // Already the real client behind a trusted proxy (see RateLimitingFilter);
        // a raw X-Forwarded-For would let callers forge the audited IP.
        return request == null ? null : request.getRemoteAddr();
    }

    static String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }
}
