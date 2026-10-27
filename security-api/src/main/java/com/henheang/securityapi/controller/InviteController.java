package com.henheang.securityapi.controller;

import com.henheang.securityapi.payload.InviteRequest;
import com.henheang.securityapi.payload.InviteResponse;
import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.securityapi.service.AccountAccessService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up invites for invite-only mode (see AccountAccessService). */
@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InviteController extends BaseController {

    private final AccountAccessService accountAccessService;

    // Inviting an address that already has a pending invite re-sends it.
    @PostMapping
    public Object invite(
            @Valid @RequestBody InviteRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        AccountAccessService.InviteResult result =
                accountAccessService.invite(request.getEmail(), admin.getId());
        return ok(InviteResponse.from(result.invite(), result.emailSent()));
    }

    @GetMapping
    public Object listInvites() {
        return ok(accountAccessService.listInvites().stream().map(InviteResponse::from).toList());
    }

    // Pending invites only; a used one is kept as the record of the sign-up.
    @DeleteMapping("/{id}")
    public Object revokeInvite(@PathVariable UUID id) {
        accountAccessService.revokeInvite(id);
        return ok();
    }
}
