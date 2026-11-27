package com.henheang.hubapi.payload.session;

import java.time.Instant;
import java.util.UUID;

/**
 * The session probe's payload (Auth V2 contract, note 05 §3).
 *
 * @param userId the verified Supabase user id
 * @param aal {@code aal2} only when the token says exactly that; anything else is reported as
 *     {@code aal1} - an unknown level is never upgraded
 * @param expiresAt the access token's expiry
 * @param mfaRequiredForInbox whether the Inbox needs {@code aal2} (pilot policy H12)
 */
public record HubSessionResponse(
        UUID userId, String aal, Instant expiresAt, boolean mfaRequiredForInbox) {}
