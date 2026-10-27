# Importing H-Space's Supabase accounts

One-off step in moving H-Space off Supabase. It copies H-Space's accounts from
the Supabase project's `auth.users` into this service's `users` table:

- **Same UUID.** Every `user_id` in H-Space's data stays valid, and the
  Supabase bridge (`POST /api/auth/supabase-token`) finds the matching
  `auth.users` row instead of answering 409.
- **Same bcrypt hash.** Passwords keep working; nobody has to reset.
- **Only H-Space users.** The Supabase project is shared with Orbit/Luyra, so
  only accounts with a `public.kori_profiles` row are read. Deleted, anonymous
  and email-less accounts are skipped.
- **Google.** An account with a linked Google identity is imported as
  `GOOGLE` (with its Google subject), so "Continue with Google" signs into it.
  Its password hash is kept too, if it has one.
- Email verification state carries over (`email_confirmed_at`). Each imported
  user gets `ROLE_USER`.

Code: `SupabaseUserImportRunner` (config), `SupabaseAuthUserReader`
(security/supabase), `SupabaseUserImportServiceImpl`.

## Safety

- **Dry run by default.** Nothing is written unless you pass
  `--supabase.import.dry-run=false`.
- **Idempotent.** Re-running skips accounts already imported (same id).
- **Never overwrites.** If a *different* Spring account already uses an
  imported email, that email is reported as a conflict and left alone.
- **All-or-nothing.** The write runs in one transaction.
- **Read-only on Supabase.** It only runs `select`s there.

## Running it

1. Get the Supabase Postgres connection: Dashboard → **Connect** → *Session
   pooler* (JDBC form). The user is `postgres.<project-ref>`; the password is
   the database password, not an API key. Keep both out of tracked files.

2. Dry run — starts without the web server, prints the report, exits:

   ```bash
   export SUPABASE_DB_URL='jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require'
   export SUPABASE_DB_USERNAME='postgres.<project-ref>'
   export SUPABASE_DB_PASSWORD='…'

   ./gradlew :security-api:bootRun --args='--supabase.import.enabled=true --spring.main.web-application-type=none'
   ```

   Point it at the database you want to import *into* the same way you run the
   service (profile / `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`).

3. Read the `Supabase user import:` log line. For each **conflict**, decide
   which account to keep. Typically it's a Spring account you created while
   testing the new sign-in, before importing: sign in as it and delete it with
   `DELETE /api/users/{id}` (soft delete — frees the email), then re-run.

4. Import for real:

   ```bash
   ./gradlew :security-api:bootRun --args='--supabase.import.enabled=true --supabase.import.dry-run=false --spring.main.web-application-type=none'
   ```

After this, existing users sign in with their old password (or Google) and the
H-Space frontend loads their existing data through the bridge.

## Accounts created after the import

Sign-ups on the Spring service after this run are already in both places: the
bridge creates their `auth.users` row with the Spring UUID on first use. Running
the import again is harmless — they show up as "already imported".

Delete this runner with the rest of the Supabase bridge once H-Space no longer
reads Supabase.
