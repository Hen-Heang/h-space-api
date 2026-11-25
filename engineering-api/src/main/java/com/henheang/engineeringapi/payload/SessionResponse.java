package com.henheang.engineeringapi.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * What engineering-api knows about the caller: only what the verified token says.
 *
 * @param ownerId the token's {@code sub} (the caller's own Supabase user id)
 * @param assuranceLevel the token's {@code aal}; always {@code aal2} here, since anything less is
 *     refused before this is built
 * @param expiresAt when the token expires
 */
public record SessionResponse(UUID ownerId, String assuranceLevel, Instant expiresAt) {}
