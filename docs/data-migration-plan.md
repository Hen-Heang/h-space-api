# Moving H-Space data from Supabase to Spring

Status: **draft, not started.** Sign-in already runs on `security-api`; every
feature record is still in Supabase under RLS, reached through the temporary
bridge (`POST /api/auth/supabase-token`, see `docs/security-api-state.md`).
This plan moves the data one area at a time. The bridge stays until the last
area has moved, then is deleted.

## Where things stand (2026-10-30)

- The web app (`E:\Fullstack\h-space\apps\web`) queries Supabase directly from
  39 files across 25+ tables. Heaviest: `kori_corrections`, `tasks`,
  `kori_vocab_cards`, `kori_profiles`, `goals`.
- Supabase also runs scheduled SQL jobs that read these tables:
  `universal_reminders` (checks the linked task/goal/note still exists),
  holiday and weather alerts (read/update `kori_profiles`), Telegram sends.
  A table can't move until whatever reads it inside Supabase has a
  replacement.
- Next.js server routes (`app/api/ai/*`, `lib/server/memory-retrieval.ts`)
  query Supabase with the caller's bridged token and rely on RLS.
- `lib/database.types.ts` is stale (missing columns added by later
  migrations) — use the SQL migrations as the schema reference.
- Spring has no data endpoints yet: `open-api` is a sample, `finance-api` an
  empty scaffold.

## Order

| # | Area | Why this position |
|---|---|---|
| 1 | **Notes** (`kori_notes`) | Pilot. One frontend module (`lib/api/notes.ts`), one server reader, one reminder check; no table depends on it. Proves the whole path (schema, API, auth, import, cutover, rollback) at low risk. |
| 2 | **Profile** (`kori_profiles`) | Sign-in writes it (`ensureProfile`), so moving it takes the bridge off the sign-in path. Needs the holiday/weather/push jobs moved first. |
| 3 | **Goals + tasks + phases** (`goals`, `tasks`, `goal_plan_phases`, …) | Core of the product but the most linked (key results, schedule rules, reminders). Moves together as one area. |
| 4 | **Reminders** (`kori_reminders` + its scheduled job) | Needs a Spring scheduler; after 3, since it checks goal/task existence. |
| 5+ | Korean study data, inbox, journal, … | One area at a time, same template. Finance follows `docs/finance-api.md`. |

Each area gets its own section below, filled in from the template before any
code is written.

## Where the code goes

**Proposal:** a new sibling module **`hub-api`** (port 8084, `/api/hub/**`),
depending on `security-api` exactly like `finance-api`:

```
api-core <- security-api <- open-api / finance-api / hub-api
```

It holds the personal-hub domains (notes, profile, goals, tasks, reminders).
`open-api` stays a sample; finance stays separate. Same Postgres as
`security-api`, so `user_id` can reference `users(id)`; Flyway is the schema
authority, `ddl-auto=validate`, tests in their own schema (`hub_api_test`)
like `finance-api`.

*Open decision:* new `hub-api` vs. one module per area. One module is less
deployment and config to run on a single small server; split later if an
area grows.

## Area 1 — Notes (pilot)

### Acceptance criteria

- Notes list, open, search, create, edit, pin and delete work in the web app
  against `hub-api`, with no change to the notes UI.
- Every note a user had in Supabase is in `hub-api` with the same `id`,
  `slug`, content and timestamps (per-user count + checksum match).
- A user can never read, change or delete another user's note (tested).
- Switching back to Supabase is one config flag, and the rollback is rehearsed
  once on a copy before production.

### Ownership

After cutover `hub-api` is the only source of truth for notes. The Supabase
table is made read-only at cutover and kept, untouched, for 30 days as the
rollback copy, then dropped by a Supabase migration.

### Schema (`hub-api` Flyway `V1__notes.sql`)

Same columns as `kori_notes` today (base table + `20260726130000`
additions), named `notes`:

- `id uuid` PK (kept from Supabase), `user_id uuid not null references users(id)`.
- `slug`, `title`, `description`, `icon`, `category`, `note_type` (same CHECK
  list), `tags text[]`, `pinned`, `source_url` (≤ 2000), `content`,
  `created_at`, `updated_at`, `version` (optimistic locking, like other
  entities).
- `unique (user_id, slug)` — notes are addressed by slug per user.
- `search tsvector generated always as (...) stored` + GIN index — same
  definition, so search results match today's.
- `goal_id`, `task_id`, `inbox_item_id`: **plain UUIDs, no foreign keys** —
  goals, tasks and inbox items are still in Supabase. Their `on delete set
  null` behaviour is lost until those areas move; the frontend already treats
  a link to a missing item as no link. Re-add the FKs when area 3 lands.

### API contract (`/api/hub/notes`, `ApiResponse` envelope, camelCase)

| Method + path | Body / query | Returns |
|---|---|---|
| `GET /api/hub/notes` | — | `NoteMeta[]`, newest `updatedAt` first |
| `GET /api/hub/notes?q=…` | websearch query | `NoteMeta[]`, ≤ 50, same ranking rules as today |
| `GET /api/hub/notes/{slug}` | — | `Note` (meta + `content`) |
| `POST /api/hub/notes` | `NoteInput` | `Note` (201) |
| `PATCH /api/hub/notes/{slug}` | partial `NoteInput` | `Note` |
| `DELETE /api/hub/notes/{slug}` | — | empty |

Shapes are the existing `NoteMeta` / `Note` / `NoteInput` from
`apps/web/lib/notes.ts`, so `notesApi`'s return types don't change. Errors:
400 validation, 404 unknown slug **or another user's note**, 409 duplicate
slug, 401 without a token. A frontend contract test pins these shapes.

### Authorization (replaces RLS)

- The user comes only from the JWT principal; `user_id` is never accepted
  from a request body or path.
- Every repository query is scoped by `user_id`
  (`findByUserIdAndSlug`, …); there is no unscoped finder.
- Another user's note answers 404, not 403, so slugs don't leak.
- Rate limiting isn't needed beyond what the JWT already gives; revisit if
  search gets expensive.

### User mapping

Accounts were imported into `users` with their Supabase UUIDs
(`docs/supabase-user-import.md`), and accounts created since get the same
UUID in Supabase via the bridge. So `kori_notes.user_id` maps to
`users.id` unchanged. Rows whose `user_id` has no `users` row are reported by
the import and skipped, not guessed.

### Data copy

A one-shot import command in `hub-api`, modelled on
`SupabaseUserImportRunner`: reads `kori_notes` over the existing
`SUPABASE_DB_*` connection, **dry-run by default**, upserts by `id`
(idempotent, safe to re-run), and prints per-user counts and a content
checksum for both sides. Never logs note content.

### Other consumers to change

- **Web app** `lib/api/notes.ts`: talk to `hub-api` through the same
  `authRequest` client as auth, behind `NEXT_PUBLIC_NOTES_BACKEND=supabase|spring`
  (default `supabase` until cutover).
- **Ask Hengo** `lib/server/memory-retrieval.ts`: its notes search moves to
  `GET /api/hub/notes?q=`. The route then needs the caller's Spring access
  token, not only the bridged Supabase one — add that to the request.
- **Reminders job** (`universal_reminders`): its `'note'` existence check can't
  see Spring. Until reminders move (area 4), treat note reminders as owned
  (`when 'note' then true`); a reminder for a deleted note then fires once
  and the app shows the note as gone. *Open decision:* accept that, or have
  Spring cancel the reminder when a note is deleted (needs a service-role
  call into Supabase — more moving parts for a pilot).

### Cutover

1. Ship `hub-api` with the endpoints and the import command; the web app
   still on `supabase`.
2. Dry-run the import against production; fix anything it reports.
3. **Freeze**: make `kori_notes` read-only in Supabase (RLS: drop the
   insert/update/delete policies) so nothing changes mid-copy.
4. Run the import for real; compare per-user counts and checksums.
5. Flip `NEXT_PUBLIC_NOTES_BACKEND=spring` and deploy the web app.
6. Smoke test: list, search, open, create, edit, pin, delete; Ask Hengo
   finds a note.

Single-owner, invite-only app: a few minutes of read-only notes between 3
and 5 is acceptable; announce nothing beyond that.

### Rollback

- Before step 5: un-freeze Supabase; nothing else changed.
- After step 5: flip the flag back to `supabase`, then run the export
  direction of the same command (notes created/changed in `hub-api` since
  cutover, by `updated_at`, upserted back into `kori_notes`), then un-freeze.
- Rehearse once on a copy of the data before step 3.

### Tests (`./gradlew :hub-api:build`, real Postgres test schema)

- CRUD happy paths; slug rename; pin toggle.
- Validation: bad `note_type`, `source_url` > 2000, missing title/slug.
- Duplicate slug for the same user → 409; same slug for two users → both OK.
- Other user's note: GET/PATCH/DELETE → 404, and their list/search never
  includes it.
- No token / expired / revoked token → 401.
- Search: matches title/description/content, capped at 50, scoped to user.
- Import: dry-run writes nothing; re-run is idempotent; orphan `user_id` is
  reported; checksums match.
- Migration: `V1__notes.sql` applies to an empty schema and `validate` passes.
- Web app: `lib/api/notes.test.ts` covers both backends with the same
  expectations.

## Template for the next areas

Copy this for each area before writing code: acceptance criteria, ownership,
schema, API contract, authorization, user mapping, data copy, other consumers
(including Supabase jobs and server routes), cutover, rollback, tests.

## Open decisions

1. `hub-api` as one module for the personal-hub areas, or one module per area.
2. Note reminders during the pilot: treat as owned, or cancel from Spring.
3. Where `hub-api` runs in production (same host/container as
   `security-api`?) — ties into the open deployment choice in `CLAUDE.md`.
