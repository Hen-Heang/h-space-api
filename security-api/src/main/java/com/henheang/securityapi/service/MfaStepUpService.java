package com.henheang.securityapi.service;

import java.util.UUID;

/**
 * Step-up: proves a signed-in user still holds their second factor before a sensitive action (MFA
 * disable, backup-code regeneration, H-Vault reset and keyset replacement). A session alone - which
 * may be stolen and lives for days - isn't enough for these.
 *
 * <p>Same rules as the sign-in MFA challenge, so it's no easier to brute-force: an authenticator
 * code is accepted once (its time step is claimed, as at sign-in), a backup code is consumed, and a
 * wrong code counts toward the account lock shared with wrong passwords ({@link
 * AccountLockoutService}).
 *
 * <p>Returns an outcome instead of throwing, so each caller answers in its own terms - hub-api's
 * vault mustn't answer 401, which the web app treats as "signed out".
 */
public interface MfaStepUpService {

    enum Result {
        VERIFIED,
        /** Wrong, replayed or missing code; counted toward the lock. */
        INVALID,
        /** The account is locked by too many wrong secrets; nothing was checked. */
        LOCKED,
        /** The account has no MFA to step up with. */
        MFA_NOT_ENABLED
    }

    /**
     * @param action a short label for the audit log, e.g. {@code "vault-reset"}; never a secret
     */
    Result verify(UUID userId, String code, String action);
}
