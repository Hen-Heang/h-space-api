package com.henheang.hubapi.security;

import java.util.UUID;

/**
 * Reads whether a verified Supabase user is currently admitted to H-Space (decision H13). Asked on
 * every pilot request - there is deliberately no positive cache - so a suspension takes effect on
 * the caller's next request.
 */
public interface AdmissionClient {

    enum Admission {
        /** An {@code approved} admission row for this user. */
        APPROVED,
        /** No row, a {@code suspended} row, or any other status: not a member. */
        DENIED,
        /** The authority couldn't answer (timeout, error, malformed reply): fail closed, 503. */
        UNAVAILABLE
    }

    /**
     * @param userId the verified token's subject; the lookup is for this user only
     * @param bearerToken the caller's own verified access token, forwarded so the authority's row
     *     security sees the caller - never a service credential
     */
    Admission check(UUID userId, String bearerToken);
}
