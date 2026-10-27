package com.henheang.securityapi.service;

import com.henheang.securityapi.security.supabase.SupabaseAuthUser;
import com.henheang.securityapi.security.supabase.SupabaseUserImportReport;
import java.util.List;

/**
 * One-off move of H-Space's Supabase accounts into this service, keeping each account's UUID (so
 * every {@code user_id} in H-Space's data stays valid) and its bcrypt hash (so passwords keep
 * working). Idempotent: re-running skips accounts already imported.
 */
public interface SupabaseUserImportService {

    SupabaseUserImportReport importUsers(List<SupabaseAuthUser> users, boolean dryRun);
}
