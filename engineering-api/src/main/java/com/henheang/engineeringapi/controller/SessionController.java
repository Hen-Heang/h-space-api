package com.henheang.engineeringapi.controller;

import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.engineeringapi.payload.SessionResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I to this API?" - a cheap end-to-end check that a Supabase session gets through the whole
 * chain (signature, issuer, audience, owner allowlist, aal2) before any feature depends on it.
 */
@RestController
@RequestMapping("/api/engineering")
public class SessionController {

    @GetMapping("/session")
    public ApiResponse<SessionResponse> session(@AuthenticationPrincipal Jwt jwt) {
        SessionResponse session =
                new SessionResponse(
                        UUID.fromString(jwt.getSubject()),
                        jwt.getClaimAsString("aal"),
                        jwt.getExpiresAt());
        return ApiResponse.<SessionResponse>builder()
                .status(StatusCode.SUCCESS)
                .data(session)
                .build();
    }
}
