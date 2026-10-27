package com.henheang.securityapi.security.supabase;

import java.util.List;

/**
 * Outcome of one import run. Conflicts are emails that already belong to a different Spring
 * account; the importer never touches those - an operator resolves them (e.g. deletes a test
 * account created before the import) and re-runs.
 */
public record SupabaseUserImportReport(
        boolean dryRun,
        List<String> imported,
        List<String> alreadyImported,
        List<String> conflicts,
        List<String> withoutPassword) {

    public String summary() {
        return String.format(
                "%s %d user(s), %d already imported, %d conflict(s)%s; %d without a password"
                        + " (Google-only or never set - they sign in with Google or reset it)",
                dryRun ? "Would import" : "Imported",
                imported.size(),
                alreadyImported.size(),
                conflicts.size(),
                conflicts.isEmpty() ? "" : " " + conflicts,
                withoutPassword.size());
    }
}
