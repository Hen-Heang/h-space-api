package com.henheang.hubapi.controller;

import com.henheang.hubapi.payload.session.HubSessionResponse;
import com.henheang.securityapi.controller.BaseController;
import com.henheang.supabaseauth.SupabaseSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth V2 session probe. Reaching this method means the pilot chain verified the token and found a
 * current admission; {@code aal1} is allowed so the web app can learn that the Inbox still needs
 * the authenticator code. Returns only the caller's own summary - no claims, tokens, profile or
 * factors. Exists only while the pilot is on.
 */
@RestController
@ConditionalOnProperty(name = "hub.supabase-auth.enabled", havingValue = "true")
@Tag(name = "Session", description = "Auth V2 pilot: the caller's own H-Space session summary")
public class HubSessionController extends BaseController {

    /** Decision H12 (proposed): every Inbox pilot request needs {@code aal2}. */
    static final boolean INBOX_REQUIRES_MFA = true;

    @Operation(summary = "The caller's admitted H-Space session")
    @GetMapping("/api/hub/session")
    public Object session(@AuthenticationPrincipal Jwt jwt) {
        return ok(
                new HubSessionResponse(
                        SupabaseSession.userId(jwt),
                        SupabaseSession.hasMfa(jwt) ? "aal2" : "aal1",
                        jwt.getExpiresAt(),
                        INBOX_REQUIRES_MFA));
    }
}
