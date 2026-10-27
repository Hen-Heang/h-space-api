package com.henheang.securityapi.controller;

import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.securityapi.service.SupabaseBridgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary - see SupabaseBridgeService. Delete with it once H-Space stops reading Supabase. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
        name = "Supabase bridge",
        description = "Temporary token exchange while H-Space data moves off Supabase")
public class SupabaseBridgeController extends BaseController {

    private final SupabaseBridgeService supabaseBridgeService;

    @Operation(
            summary = "Issue a Supabase access token",
            description =
                    "Exchanges this service's access token for a short-lived token that Supabase"
                            + " row-level security accepts as the same user. Creates the user's"
                            + " Supabase auth record on first use. 503 when the bridge isn't"
                            + " configured; 409 when the email belongs to a pre-migration Supabase"
                            + " account that hasn't been imported yet.")
    @PostMapping("/supabase-token")
    public Object issueSupabaseToken(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ok(supabaseBridgeService.issueToken(userPrincipal.getId()));
    }
}
