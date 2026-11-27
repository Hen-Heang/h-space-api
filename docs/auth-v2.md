# Auth V2 pilot — Supabase sessions on hub-api's session probe and Inbox

Status (2026-11-27): **merged (#58, `7c3adf0`) and deployed to the Railway
sandbox with the pilot OFF.** Railway deployment `eb51177a` succeeded: hub-api
started on `prod` with no errors, the pilot and the old Supabase bridge both
off. `V3__app_users.sql` is **confirmed applied**, checked read-only on the
sandbox's database (Neon project `h-space-api-railway-sandbox`, database
`jwt_auth`): `flyway_hub_schema_history` has `3 | app users | success = true`,
installed 2026-11-27 04:23:54 UTC during that deployment, and
`inbox_items_user_id_fkey` now references `app_users`. Both tables were empty
at the time. (`prod` logs at WARN, so these Flyway lines never appear in the
Railway logs; the queries used were:)

```sql
select version, description, success from flyway_hub_schema_history order by installed_rank;
select confrelid::regclass from pg_constraint where conname = 'inbox_items_user_id_fkey';
```

The service has no public domain,
and no live Supabase change was made (`kori_app_access` is not applied).
Codex review findings fixed before merge: the admission reply is bounded
including its body (`e7639e4`), and the Inbox import runbook anchors owners
in `app_users` (`572e20d`). Design source: the owner's vault notes "API
Migration" 10 (spec), 11 (test plan), 05 (contracts), 08 (decisions).
"Auth V2" is a design revision, not a package upgrade.

## What it is

With `HUB_SUPABASE_AUTH_ENABLED=true`, a second security chain in hub-api owns
exactly `GET /api/hub/session` and `/api/hub/inbox-items/**`. It accepts **only**
Supabase access tokens (the web app's own session) and runs four separate gates
before any controller:

| # | Gate | Where | Failure |
|---|---|---|---|
| 1 | Token: ES256/RS256 from `<HUB_SUPABASE_URL>/auth/v1/.well-known/jwks.json`, exact issuer, `aud` contains `authenticated`, `exp` required, 60 s skew | `supabase-auth` `SupabaseJwtDecoders` | 401 |
| 2 | Subject: `sub` is a UUID (`SUPABASE_USER`) | `SupabaseAuthenticationConverter` | 403 |
| 3 | Admission: the caller's own `kori_app_access` row is exactly `approved`, read **on every request** through Supabase's Data API with the publishable key + the caller's own bearer token (no service key, no cache) | `HubAdmissionFilter`, `SupabaseDataApiAdmissionClient` | 403 not admitted / 503 authority unavailable |
| 4 | Assurance: Inbox needs `aal2` (`SUPABASE_MFA`, decision H12); the probe accepts `aal1` | `HubSupabaseSecurityConfig` | 403 (MFA message) |
| 5 | Ownership: every Inbox query scoped by the verified user id; another user's item is 404 | `InboxItemServiceImpl`, `HubCaller` | 404 |

A feature flag selects the path; it never relaxes a gate. With the pilot on
there is **no legacy-JWT fallback** on these paths (a valid security-api JWT
gets 401), and a Supabase token gets nothing on legacy routes (`/api/auth/**`,
`/api/hub/vault/**`, `/api/finance/**` keep security-api's chain). With the
pilot off, nothing here is active and the Inbox works exactly as on `main`.

Errors use the usual `ApiResponse` envelope with `requestId`; messages are fixed
text (no token, exception or library detail). Tokens and admission replies are
never logged.

### Session probe

`GET /api/hub/session` → `200 {userId, aal ("aal1"|"aal2", unknown is reported
as aal1), expiresAt, mfaRequiredForInbox: true}`. Nothing else: no claims,
email, profile or factors.

### Inbox contract

Unchanged from `main`: create/delete answer **200** with the envelope (not
201/204); no `version` field and no 409; PATCH absent = unchanged, explicit null
clears a nullable field.

## Isolation from security-api

- `HubSupabaseSecurityConfig` is `@Order(HIGHEST_PRECEDENCE)` with a
  `securityMatcher` for the pilot paths only; security-api's catch-all chain
  still handles everything else.
- security-api's `JwtAuthenticationFilter` is a `@Component`, so Spring Boot
  also registered it for every request in the servlet container. With the pilot
  on, that container registration is disabled (`FilterRegistrationBean`,
  `enabled=false`); on legacy routes it still runs inside security-api's chain,
  which is where it ran first anyway, so legacy behaviour is unchanged.
- The pilot's `JwtDecoder` is not a bean (so Spring Boot's resource-server
  auto-configuration can't attach it to other chains), and `HubAdmissionFilter`
  is not a `@Component` (so it isn't registered globally).
- `security-api` component scanning was **not** removed: Finance, Vault,
  `BaseController`, `BaseEntity`, `GlobalExceptionHandler`, Flyway ordering and
  scheduled jobs still depend on it.

## Data: `app_users` (decision H4, proposed)

`db/hub-migration/V3__app_users.sql` adds `app_users (id, created_at)` and
re-points `inbox_items.user_id` from security-api's `users` to it, so a
Supabase-only user can own Inbox rows **without** a password row. It backfills
the owners of existing items. The Inbox service upserts the caller's own row
(`INSERT ... ON CONFLICT DO NOTHING`) on create, in both modes, only after every
gate passed.

**Review before merge:** this migration runs whether or not the pilot is on;
deleting a legacy `users` row no longer cascades to that user's inbox items.
Applied so far only to local test schemas (`hub_api_test`, `auth_v2_local`).

`V4__inbox_item_tombstones.sql` (P1-R1, proposed, 2026-11-27) adds
`inbox_item_tombstones (id, user_id → app_users, deleted_at)`. In both modes, a
delete that passed every gate and the ownership check writes one tombstone in
the same transaction. The Inbox contract and every gate are unchanged. Purpose
and runbook: `docs/spring-migration-plan.md` (Inbox cutover runbook).

## Admission table (web repo)

`h-space` `apps/web/supabase/migrations/20261127120000_kori_app_access.sql`
(authored, **not applied**): `kori_app_access (user_id PK → auth.users, status
approved|suspended, timestamps)`, RLS select-own-row, `SELECT (user_id, status)`
to `authenticated` only, no browser writes. Deliberately **without** the
restrictive "MFA when enrolled" policy so the status is readable at aal1.
Ordering hazard: re-running `20261121120000_require_mfa_when_enrolled.sql`
after it re-adds that policy and locks MFA users out of their own status — the
migration drops it, and `apps/web/supabase/tests/kori_app_access.test.sh`
proves both the hazard and the remedy. H6 must decide how the two coexist.

## Configuration (names only)

| Variable | Default | Meaning |
|---|---|---|
| `HUB_SUPABASE_AUTH_ENABLED` | `false` | Turns the pilot chain on |
| `HUB_SUPABASE_URL` | — | Project URL; must be `https` (plain `http` only for loopback). Issuer = `<url>/auth/v1` |
| `HUB_SUPABASE_PUBLISHABLE_KEY` | — | The **publishable** key. Startup refuses `sb_secret_…` or a `service_role` JWT |
| `HUB_SUPABASE_AUDIENCE` | `authenticated` | Expected `aud` |
| `HUB_SUPABASE_ADMISSION_TIMEOUT` | `3s` | One admission read; slower = 503 (max 10 s) |
| `CORS_ALLOWED_ORIGINS` | existing | Same CORS source as security-api; must list the web origin |

Never used here: `SUPABASE_JWT_SECRET`, `SUPABASE_SERVICE_ROLE_KEY`, any
signing key. New names on purpose: the retired bridge's `SUPABASE_URL` etc. are
not reused. hub-api still needs its legacy settings (`JWT_SECRET`,
`MFA_ENCRYPTION_KEY`, …) to boot, because it still embeds security-api.

## Run it locally (synthetic only)

No real Supabase is needed or contacted. `scripts/auth-v2-local/fake-supabase.mjs`
(dev-only, binds 127.0.0.1) serves an in-memory ES256 JWKS, answers the
admission read, and mints synthetic tokens; hub-api still verifies them fully.

```bash
node scripts/auth-v2-local/fake-supabase.mjs            # :54329
./gradlew :hub-api:bootJar
SPRING_PROFILES_ACTIVE=test \
SPRING_DATASOURCE_HIKARI_SCHEMA=auth_v2_local SPRING_FLYWAY_DEFAULT_SCHEMA=auth_v2_local \
HUB_SUPABASE_AUTH_ENABLED=true HUB_SUPABASE_URL=http://127.0.0.1:54329 \
HUB_SUPABASE_PUBLISHABLE_KEY=sb_publishable_local_synthetic_only \
CORS_ALLOWED_ORIGINS=http://localhost:3000 \
java -jar hub-api/build/libs/hub-api-0.0.1-SNAPSHOT.jar   # :8084, local test DB, own schema

U=aaaaaaaa-0000-4000-8000-00000000a001
curl -X POST "http://127.0.0.1:54329/__admission?user=$U&status=approved"
T=$(curl -s -X POST "http://127.0.0.1:54329/__mint?sub=$U&aal=aal2" | jq -r .access_token)
curl -H "Authorization: Bearer $T" http://127.0.0.1:8084/api/hub/session
```

Web side: see `h-space` `apps/web/docs/auth-v2.md`.

**Disable / revert:** unset `HUB_SUPABASE_AUTH_ENABLED` (or `false`) and restart —
the pilot chain, probe and filter registration disappear. Code revert = drop
this branch. V3 is a schema change: reverting it needs a reviewed down-migration
(re-point the FK to `users` after confirming every owner has a `users` row).

## Tests (case ids from the vault test plan)

- `supabase-auth`: `SupabaseJwtDecodersTest` (extraction parity), 
  `SupabaseJwtDecodersHardeningTest` (A1-H: required exp, missing iss, aud
  arrays, nbf skew, malformed exp, https issuer, jku/x5u/jwk never followed,
  key rotation / unknown kid / JWKS outage against a loopback key server),
  `SupabaseAuthenticationConverterTest` (incl. unknown `aal`, role/scope claims).
- hub-api: `SupabasePilotIntegrationTest` — real decoder + JWKS fetch from a
  loopback stub, real Postgres: ACC-01/02/03/06/07/08, JWT-02/03/05/07/08/09,
  ISO-01/02/05/06/07/08, MFA-01/02, INBOX-02/04/07/08.
  `HubSupabaseAuthPropertiesTest` (JWT-11 for the config). All pre-existing
  hub/finance/vault/security tests run unchanged with the pilot off (ISO-04).

Not proven here: anything about the live Supabase project (signing keys, TTL,
RLS, providers), real browser sessions, Orbit, MCP/Realtime/Storage/background
actors (ACC-10–13), live session revocation (SES-05). Those stay release gates.

## A1 extraction vs. A1-H hardening

Behaviour-preserving extraction = the library + engineering-api delegating to
it (engineering-api's 32 tests passed unchanged before any hardening).
Hardening, a deliberate behaviour change to review on its own: `exp` is now
required and the issuer must be `https` (or loopback `http`). Both apply to
engineering-api too; real Supabase tokens always carry `exp` and its issuer is
https, so no legitimate caller is expected to change.

## Decisions (H1–H15) as used here

| Id | Used locally as | Status |
|---|---|---|
| H1 Supabase is the product IdP | yes | proposed, owner approval pending |
| H3 library placement | separate `supabase-auth` module, no project deps | proposed |
| H4 Inbox FK | `app_users` anchor (V3) | **merged by the owner 2026-11-27**; live in the Railway sandbox, FK re-point confirmed (see Status) |
| H5 signing keys / legacy secret | not touched; pilot refuses HS256 | live check pending |
| H6 MFA RLS migration | not applied; ordering hazard documented + tested | pending |
| H9 login methods | Google/password/email code unchanged; GitHub flag-off | proposed |
| H10 registration | owner provisioning; no signup endpoint added | proposed |
| H12 pilot MFA | Inbox requires aal2 | proposed |
| H13 admission | `kori_app_access`, per-request, caller token | proposed, table not applied |
| H14 TTL / revocation | not changed; admission per request; no live session check | pending (Orbit) |
| H15 GitHub callback | isolated PKCE client on the web side, flag-off | proposed, provider not configured |
| H2, H7, H8, H11 | out of this change | unchanged |
