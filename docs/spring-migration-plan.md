# Moving H-Space data from Supabase to Spring

Status: **Phase 0 done, Phase 1 (Inbox) built — not cut over.** Started 2026-10-30.

Sign-in is already Spring-only (`security-api`). What still runs on Supabase is
the H-Space web app's **data**: every feature reads and writes Supabase tables
under row-level security (RLS), reached with a short-lived token from the
temporary bridge (`/api/auth/supabase-token`). This plan moves that data into
Spring one area at a time, with the app working at every step, and ends by
deleting the bridge.

Per the root `CLAUDE.md`, no area moves without the checklist in
[Per-area checklist](#per-area-checklist), and the first move is a small tested
vertical slice (the Inbox).

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **One product module, `hspace-api`**, one package set per area. Not one app per area, and not inside `security-api`. | The design doc's modular monolith (§3.1): one port, one deploy, one Flyway history. Keeps auth (`security-api`) separate from product data. |
| D2 | `hspace-api` **embeds `security-api`** (component scan, like `open-api`), so it validates the same JWTs against the same database: filter chain, revoked tokens, email-verified check, `GlobalExceptionHandler`, `ApiResponse` envelope. | No second auth implementation; a revoked token is revoked everywhere. |
| D3 | Product tables live in the **same PostgreSQL database** as `security-api`, default schema, with `user_id UUID REFERENCES users(id)`. | Users already share UUIDs with Supabase (account import), so rows copy across with their ids and owners unchanged. |
| D4 | `hspace-api` migrations are numbered **V100+**; `out-of-order: true` in `hspace-api`. | `security-api` keeps V1–V99 without collisions (`docs/database.md`). |
| D5 | **Ownership replaces RLS in the service layer**: every repository query takes the caller's `userId` from the JWT; another user's id answers **404**; the owner never comes from the request body. | Design doc §5.1. Tested per area (other-user tests). |
| D6 | Each moved area gets a **frontend data-source flag** (`NEXT_PUBLIC_DATA_SOURCE_<AREA>=spring`), default off. | Cutover and rollback are a config change; the Supabase table is left untouched by the copy. |
| D7 | Next.js AI routes keep authenticating with the Supabase bridge token until the areas they read have all moved; for an area already in Spring the browser also sends its h-space-api token (`x-hspace-token`, same-origin only) and the route reads that area from `hspace-api`. | Avoids changing token signing now. Revisit in Phase 4 (see [Open items](#open-items)). |
| D8 | The H-Vault (`kori_vault_accounts`) **does not move** in this plan. | It's behind the separate security gate in `CLAUDE.md` (threat model + independent review first). |
| D9 | Luyra (`apps/luyra`) is **out of scope**: its own Supabase Auth and finance data. Its read of H-Space learning tables moves when Learning moves. | Separate app and data owner. |

## What depends on Supabase today

Measured 2026-10-30 in `h-space` `apps/web` (excluding `dev-learning-notes` and tests):

- **30** `lib/api/*` modules, about **370** `.from()`/`.rpc()` calls, **69** tables.
- **9** RPCs: `join_goal`, `get_goal_by_share_code`, `regenerate_goal_share_code`,
  `get_goal_members`, `remove_goal_member`, `get_enriched_notifications`,
  `search_users_profile`, `kori_next_reminder_run`, `kori_record_skill_event`.
- **1** storage bucket: `kori-avatars` (`lib/api/user.ts`).
- **14** Next.js route handlers (`app/api/ai/*`, `app/api/integrations/*`) and
  **15** `lib/server`/`lib/mcp` files that authenticate with Supabase
  `auth.getUser` and query under RLS.
- **12** `pg_cron` jobs inside Supabase (3 defined in repo migrations:
  `kori-universal-reminders`, `kori-holiday-alert-reminder`,
  `kori-weather-alerts`), using `pg_net` and Supabase Vault for the Telegram
  bot token.
- **3** storage buckets live (`avatars`, `goal-themes`, `kori-avatars`).
- No Realtime subscriptions.

The live project has **71** `public` tables, all with RLS on. **34** of them
have no `CREATE TABLE` in the repo's migrations (they predate them), and 55 of
the 82 applied live migrations have no repo file. A live schema snapshot
(definitions only, no row data, read 2026-10-30) is the source for those
tables' Flyway migrations. It is kept **outside every repository**
(maintainer's machine) because both repos are public and it is a detailed
map of the live database — never commit it.

## Phases

| Phase | Areas (Supabase tables) | Extra work | Status |
|---|---|---|---|
| **0 Groundwork** | — | `hspace-api` module, conventions D1–D7, live schema snapshot, frontend `hspaceRequest` (401 → refresh once → retry), data-source flag pattern | **Done** (see below) |
| **1 Pilot: Inbox** | `kori_inbox_items` | Ask-Hengo memory search reads the inbox from `hspace-api` when flagged | **Built + tested; cutover pending** |
| 2 Small personal areas | Journal + manual activities (`kori_journal_entries`, `kori_manual_activities`); Notes (`kori_notes`); Memories (`kori_memory_candidates`); Habits (`kori_habits`, `kori_habit_checkins`) | Full-text search columns (Notes, Journal) like the Inbox; Ask-Hengo retrieval per area | Not started |
| 2b Reminders | `kori_reminders`, `kori_push_subscriptions`, `kori_push_config`, `kori_telegram_link_codes`, `kori_telegram_links`, `kori_public_holidays` | Replace the `pg_cron` jobs with Spring `@Scheduled` jobs; `kori_next_reminder_run` as service code; Telegram bot token and VAPID keys to Spring config (env/secret store, not Supabase Vault) | Not started |
| 3 Goals & tasks | `goals`, `tasks`, `goal_members`, `goal_stars`, `notifications`, `goal_key_results`, `goal_evidence`, `goal_reviews`, `goal_plan_phases`, `goal_schedule_rules` | The 6 goal-sharing/notification RPCs become service methods with explicit member/owner checks (shared goals are the one place ownership isn't just `user_id`) — write each access rule from scratch and test it, rather than translating the SQL; bucket `goal-themes`; drop the cross-store `goal_id` caveat on Inbox | Not started |
| 3b Profile | `kori_profiles`, `user_profiles`; `search_users_profile`; buckets `kori-avatars`, `avatars` | Avatar storage choice (local disk/S3-compatible); profile upsert at first sign-in moves to Spring (`auth.ts ensureProfile`) | Not started |
| 4 Learning | vocab, phrasebook (4), Korean coach (practice turns, speaking attempts, coach preferences, voice sessions), daily phrases, listening, reading, analyzer history, scenarios + scenario sessions, interview (6), missions (2), progress (achievements, activity log), skills (mastery, events + `kori_record_skill_event`), foundations, daily study plans, chat (conversations, messages, corrections), AI usage | Most AI routes write here: move them to Spring-token auth (see Open items); Luyra's learning summary reads | Not started |
| 5 Sensitive | Recovery (`kori_focus_*`, 9 tables); integrations (`kori_google_calendar_integrations`, `kori_mcp_audit`) | Recovery last (most sensitive personal data); Google Calendar refresh tokens need encryption at rest in Spring | Not started |
| 6 Remove Supabase | — | Delete the bridge (`SupabaseBridge*`), the account import (`SupabaseUserImport*`), `apps/web/lib/supabase.ts`, the Supabase env vars, `requireUser`'s Supabase check; archive or drop the Supabase tables after a retention period | Not started |

The Vault stays on Supabase (D8). Each phase is one or more PRs per area,
each following the checklist below.

## Phase 0 — what exists now

- `hspace-api` module (`settings.gradle`, root `build.gradle`), port **8084**,
  Swagger group `hspace` (`/api/v1/**`).
- Conventions: Flyway V100+ (`docs/database.md`), user-scoped repositories,
  `ResourceNotFoundException` → 404 for other users' items, `BadRequestException`
  → 400, `@Valid` DTOs → 400 with a field map, `ApiResponse` envelope.
- Test pattern: `@SpringBootTest` + MockMvc against the real Flyway schema in
  `hspace_api_test`, two users per test class, JWTs from `JwtTokenProvider`
  (`InboxItemControllerIntegrationTest`).
- Frontend (`h-space` `apps/web`):
  - `lib/api/hspace-http.ts` `hspaceRequest()`: bearer token, base URL
    `NEXT_PUBLIC_HSPACE_API_URL` (defaults to `NEXT_PUBLIC_AUTH_API_URL`),
    and **401 → refresh once → retry** (audit finding 1.13);
  - `lib/auth-store.ts` `refreshAccessToken()`;
  - `lib/feature-flags.ts` `isSpringInboxEnabled()` (`NEXT_PUBLIC_DATA_SOURCE_INBOX`);
  - `lib/api/ai-client.ts` sends `x-hspace-token` to this app's AI routes when
    an area has moved; `lib/server/hspace-inbox.ts` uses it.
- Live schema snapshot: kept off-repo (see above).

## Phase 1 — Inbox

**API** (`hspace-api`, `InboxItemController`):

| Method & path | Body / params | Notes |
|---|---|---|
| `GET /api/v1/inbox-items` | `status`, `itemType` (`all`/omitted = any), `q` (English websearch full-text), `limit` 1–500 (default 500) | Newest `capturedAt` first |
| `GET /api/v1/inbox-items/{id}` | — | 404 if not the caller's |
| `POST /api/v1/inbox-items` | `content` (required, ≤4000), `itemType` (required), `title` ≤200, `tags` ≤100 × ≤100 chars, `eventAt`, `goalId`, `source` | `status=inbox`, `capturedAt=now` |
| `PATCH /api/v1/inbox-items/{id}` | any of `title`, `content`, `itemType`, `tags`, `eventAt`, `goalId`, `pinned`, `status`, `convertedToType`, `convertedToId` | Omitted = unchanged, `null` = clear (nullable fields only) |
| `DELETE /api/v1/inbox-items/{id}` | — | Hard delete, like today |

Response fields match the web app's `InboxItem` type, so the frontend adapter
needs no mapping. `user_id` is never returned.

**Data ownership:** after cutover, `hspace-api` owns inbox items. `goal_id`
still points at a Supabase goal (no FK until Phase 3), the same weak link it
is today. Conversions still create the Note/Task/Journal entry in Supabase and
then stamp the inbox item in Spring.

**Tests:** `hspace-api` `InboxItemControllerIntegrationTest` (10 cases:
create/read, validation, filters, limits, full-text search scoped to the
caller, other user → 404 on get/patch/delete, PATCH absent vs null, invalid
PATCH values, delete, unauthenticated → 401). Frontend:
`lib/api/hspace-http.test.ts`, `lib/api/inbox.test.ts`,
`lib/server/hspace-inbox.test.ts`.

**Known gap:** malformed JSON or a non-UUID path id returns 500, not 400:
`GlobalExceptionHandler` has no handler for `HttpMessageNotReadableException`
or `MethodArgumentTypeMismatchException` (audit finding 1.6). Fix it there,
for every module, as its own change.

### Inbox cutover runbook

The export file holds personal data: keep it outside any repository, never
paste it into tools or chats, and delete it after the import.

1. **Run `hspace-api`** against the Spring database (it applies V100). Set
   `JWT_SECRET` (and the rest of `security-api`'s secrets) to the **same**
   values as the running `security-api`, or tokens won't validate.
2. **Check owners exist** — every Supabase inbox owner must be a Spring user
   with the same id (account import, `docs/supabase-user-import.md`). On
   Supabase:
   ```sql
   select user_id, count(*) from public.kori_inbox_items group by user_id;
   ```
   and on Spring, for those ids: `select id from users where id in (...);`
3. **Export** from Supabase (read-only):
   ```bash
   psql "$SUPABASE_DB_URL" -c "\copy (select id, user_id, title, content, item_type, tags, captured_at, event_at, goal_id, status, source, pinned, converted_to_type, converted_to_id, created_at, updated_at from public.kori_inbox_items order by created_at) to 'inbox_items.csv' with (format csv, header)"
   ```
4. **Import** into Spring in one transaction, through a staging table so a
   missing owner is reported instead of aborting halfway:
   ```sql
   begin;
   create temp table inbox_stage (id uuid, user_id uuid, title text, content text,
     item_type text, tags text[], captured_at timestamptz, event_at timestamptz,
     goal_id uuid, status text, source text, pinned boolean, converted_to_type text,
     converted_to_id uuid, created_at timestamptz, updated_at timestamptz);
   \copy inbox_stage from 'inbox_items.csv' with (format csv, header)
   select distinct user_id from inbox_stage where user_id not in (select id from users); -- must be empty
   insert into inbox_items (id, user_id, title, content, item_type, tags, captured_at, event_at,
     goal_id, status, source, pinned, converted_to_type, converted_to_id, created_at, updated_at)
   select id, user_id, title, content, item_type, tags, captured_at, event_at,
     goal_id, status, source, pinned, converted_to_type, converted_to_id, created_at, updated_at
   from inbox_stage on conflict (id) do nothing;
   commit;
   ```
5. **Reconcile** — run on both databases (`kori_inbox_items` / `inbox_items`)
   and compare; counts and hashes must match per user:
   ```sql
   select user_id, count(*), md5(string_agg(id::text || content || status, '|' order by id))
   from <table> group by user_id order by user_id;
   ```
6. **Switch** the web app: `NEXT_PUBLIC_HSPACE_API_URL=<hspace-api URL>`,
   `NEXT_PUBLIC_DATA_SOURCE_INBOX=spring`; redeploy. Add the web origin to
   `hspace-api`'s CORS first (see Open items).
7. **Verify** in the app: list, capture, pin, archive, convert to note/task/journal, Ask Hengo
   cites an inbox item.
8. **Delete** `inbox_items.csv`.

**Rollback:** unset `NEXT_PUBLIC_DATA_SOURCE_INBOX` and redeploy; the app reads
Supabase again. Items created or changed in Spring after step 6 must be copied
back first (the same export/import in the other direction, `on conflict (id) do
update`). Leave `kori_inbox_items` in Supabase, unused, until Phase 6.

## Per-area checklist

Copy this into the PR for every area that moves.

- [ ] **Ownership** — which tables move; who owns them after cutover.
- [ ] **Schema** — Flyway `V1xx__<area>.sql` reproducing the live table
      (from the off-repo live schema snapshot): same columns, types,
      defaults, checks; `user_id REFERENCES users(id)`; FKs to tables not yet
      moved dropped and noted.
- [ ] **RLS replaced** — read every Supabase policy for the tables; each rule
      becomes a service check. Shared data (goal members, public catalogs)
      gets explicit tests.
- [ ] **API contract** — `/api/v1/<kebab-plural>`, response fields equal to
      the frontend type, PATCH semantics documented; Swagger annotations.
- [ ] **Tests** — success, validation (400), other user (404), unauthenticated
      (401), duplicates/conflicts where relevant, against the real schema.
- [ ] **Frontend** — store adapter behind `NEXT_PUBLIC_DATA_SOURCE_<AREA>`,
      Vitest for the Spring adapter; server routes and MCP tools that read the
      table switched too (grep the table name across `apps/web`).
- [ ] **Jobs** — any `pg_cron`/trigger/RPC touching the tables has a Spring
      replacement.
- [ ] **Copy + reconcile + rollback** — runbook like the Inbox one.
- [ ] **Docs** — this plan's phase table; `docs/architecture.md` if the module
      layout changes.

## Open items

- **CORS** — origins are hard-coded to localhost (`WebSecurityConfig`,
  `CorsConfig`); a deployed web app can't call `security-api` or `hspace-api`
  until they're read from configuration (audit finding 1.16). Needed before
  any cutover outside local.
- **Configuration sharing** — `hspace-api`'s `application.yml` carries only
  the subset of `security-api` settings its tests need (like `open-api` and
  `finance-api`). Before `hspace-api` runs anywhere but locally, give it the
  same settings as `security-api` (JWT, MFA key, mail, Google client ids,
  owner email, signup mode, Supabase bridge), or extract them into one shared
  file.
- **One backend or two** — `hspace-api` already serves every `security-api`
  endpoint too. Deploying only `hspace-api` (and pointing both frontend URLs at
  it) is simplest; decide before the first production cutover.
- **AI-route auth (Phase 4)** — when most data is in Spring, switch the Next.js
  routes from Supabase `auth.getUser` to verifying the h-space-api token: either
  call `GET /api/auth/user`, or have `security-api` sign with an asymmetric key
  and publish it (JWKS) so routes verify locally. Decide then.
- **`finance-api`** (branch `feat/finance-api-module`) — under D1, finance
  becomes a package in `hspace-api` rather than its own app; fold the scaffold
  in when finance work starts.
- **Local run** — `hspace-api` has no committed `local` profile settings; copy
  `security-api`'s git-ignored `application-local.yml` to
  `hspace-api/src/main/resources/application-local.yml` (also git-ignored) so
  both use the same database and `jwt.secret`.
