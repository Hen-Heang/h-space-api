package com.henheang.securityapi.service;

import com.henheang.securityapi.payload.SupabaseTokenResponse;
import java.util.UUID;

/**
 * Temporary bridge while H-Space moves its data from Supabase to this service: mints a short-lived
 * token that Supabase (PostgREST/RLS) accepts as the same user, so domains not yet migrated keep
 * working for users who signed in here. Remove once no H-Space code queries Supabase.
 */
public interface SupabaseBridgeService {

    boolean isEnabled();

    SupabaseTokenResponse issueToken(UUID userId);
}
