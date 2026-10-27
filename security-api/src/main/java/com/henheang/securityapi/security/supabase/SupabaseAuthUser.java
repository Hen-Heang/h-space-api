package com.henheang.securityapi.security.supabase;

import java.time.Instant;
import java.util.UUID;

/**
 * One Supabase {@code auth.users} row, as read for the one-off account import (see
 * SupabaseUserImportService).
 *
 * @param encryptedPassword Supabase's bcrypt hash, or null/blank for OAuth-only accounts
 * @param googleSubject the Google account id from {@code auth.identities}, when Google is linked
 */
public record SupabaseAuthUser(
        UUID id,
        String email,
        String encryptedPassword,
        boolean emailConfirmed,
        String name,
        String avatarUrl,
        String googleSubject,
        Instant createdAt) {}
