package com.henheang.securityapi.payload;

import com.henheang.securityapi.domain.SignupInvite;
import java.time.Instant;
import java.util.UUID;

/**
 * An invite as admins see it. {@code emailSent} is only known right after sending, and is null in
 * listings.
 */
public record InviteResponse(
        UUID id,
        String email,
        UUID invitedBy,
        Instant createdAt,
        Instant usedAt,
        boolean pending,
        Boolean emailSent) {

    public static InviteResponse from(SignupInvite invite) {
        return from(invite, null);
    }

    public static InviteResponse from(SignupInvite invite, Boolean emailSent) {
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getInvitedBy(),
                invite.getCreatedAt(),
                invite.getUsedAt(),
                invite.isPending(),
                emailSent);
    }
}
