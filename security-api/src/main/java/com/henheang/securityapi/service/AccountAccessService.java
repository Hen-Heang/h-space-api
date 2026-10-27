package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.SignupInvite;
import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.UUID;

/**
 * Who may create an account, and who is the owner. Sign-up runs in one of two modes
 * (app.signup.mode): {@code open}, or {@code invite-only}, where a new account needs a pending
 * {@link SignupInvite} for its email. The owner (app.owner-email) may always create their account
 * and is made ROLE_ADMIN once their email is verified. Existing accounts are never affected.
 */
public interface AccountAccessService {

    boolean isOwner(String email);

    /** Whether a new account may be created for {@code email} right now. */
    boolean mayCreateAccount(String email);

    /** Throws AuthException(SIGNUP_INVITE_REQUIRED) unless {@link #mayCreateAccount}. */
    void assertMayCreateAccount(String email);

    /** Uses up the invite (if any) that let {@code user} be created. */
    void recordAccountCreated(User user);

    /** Grants ROLE_ADMIN to the owner's account, once its email is verified. No-op otherwise. */
    void ensureOwnerIsAdmin(User user);

    /** Startup hook: makes an existing owner account admin. */
    void ensureOwnerIsAdmin();

    /**
     * Invites {@code email} (or returns its pending invite) and emails it a sign-up link. Returns
     * the invite; the email is best effort.
     */
    InviteResult invite(String email, UUID invitedBy);

    List<SignupInvite> listInvites();

    void revokeInvite(UUID inviteId);

    record InviteResult(SignupInvite invite, boolean emailSent) {}
}
