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
        if (request == null) {
            return null;
        }
        // Resolved from X-Forwarded-For by Tomcat only when sent by a trusted
        // proxy (server.forward-headers-strategy), so it can't be spoofed.
        return request.getRemoteAddr();
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
