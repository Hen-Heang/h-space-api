# Risks and decisions

## Decisions

Status values:

- **DECIDED**: settled by the owner
- **PROPOSED**: the recommendation, which needs approval
- **BLOCKED**: needs information first

| # | Decision | Recommendation | Status | Blocks |
|---|---|---|---|---|
| D-1 | Where finance code lives | `finance-api` module (separate runnable, port 8083). `docs/spring-migration-plan.md`'s "fold into `hub-api`" note is superseded. | **DECIDED 2026-11-03** | — |
| D-2 | Target database | H-Space PostgreSQL, the same database as `security-api`'s `users`, so `user_id` can be a real FK. Finance tables live in the default schema with their own Flyway history. Luyra Neon stays the source until cutover. **Which provider hosts H-Space production (Neon or Supabase) isn't recorded in the repo.** Confirm it, and confirm backup/PITR covers finance. | **BLOCKED** (provider fact) | Phase 1 (for prod settings), 7 |
| D-3 | User identity mapping | A mapping table with `same_uuid` / `explicit` / `excluded` statuses (AUTH-AND-DATA-MIGRATION §1–2). Never match by email alone. | **BLOCKED** (needs the metadata check) | Phase 7; and Phase 1 only for the test-fixture shape |
| D-4 | How Luyra authenticates to Spring | Option A: Luyra signs in through `security-api` and sends the H-Space bearer token. No token exchange, no Supabase JWT in Spring. Confirm Luyra's Google client id goes into `GOOGLE_CLIENT_IDS` and Luyra's origin into `CORS_ALLOWED_ORIGINS`. | PROPOSED | Phase 7 |
| D-5 | Response envelope | Keep `ApiResponse`. Luyra's `lib/api/client.ts` gets a Spring adapter: unwrap `data`, map HTTP status to `error.code`, refresh once on 401. Overview SSR prefetch switches to HTTP or is dropped. | PROPOSED | Phase 1 |
| D-6 | Export endpoint | A documented exception to the envelope rule: a raw `text/csv` / `application/json` attachment, which is what Luyra serves today. | PROPOSED | Phase 4 |
| D-7 | KRW precision and rounding | KRW is whole won (`NUMERIC(14,0)`), USD `NUMERIC(12,2)`, rate `NUMERIC(14,6)`. Conversion uses exact `HALF_UP`. Accept a possible ₩1 difference from Luyra's double-based `Math.round` at exact `.5` products (found: $156.25 × 1386.1408 = 216584.5 gives Spring 216585, Luyra 216584). Pre-check the source for fractional KRW and stop if any is found. Under this rule the API rejects fractional KRW, which Luyra's API accepted. | PROPOSED; **built as proposed in Phase 1b**, pending approval | Phase 1b |
| D-8 | Duplicate-submit protection | Optional `Idempotency-Key` header on `POST /transactions` and `POST /savings/{id}/contributions`, stored as `request_id` with `UNIQUE (user_id, request_id)`. Luyra's UI can send it later; it isn't required. | PROPOSED | Phase 1b |
| D-9 | Unused schema | Don't port `finance_ai_insights`, the savings auto-plan columns (`auto_monthly_usd`, `last_auto_month`, `reminder_day`, `skipped_month`, `last_reminder_month`, `last_contribution_month`), `templates.currency`, or `share_descriptions_with_ai` (keep it only if the coach will use it). Keep `ai_coach_enabled` (Phase 6 honours it) and `monthly_spending_limit_krw` (the UI writes it). The source keeps everything for the retention window. | PROPOSED | Phase 3, 4 |
| D-10 | Telegram and web-push ownership | H-Space's migration plan phase 2b has its own `kori_telegram_links`/`kori_push_subscriptions` and reminder jobs, while Luyra has `telegram_accounts`/`push_subscriptions`. Choose one: (a) `finance-api` owns finance notifications with Luyra's tables; or (b) wait for H-Space 2b and call a shared notification service. **Recommended: (a) for now**, scoped to finance, and revisit at H-Space 2b. | PROPOSED | Phase 6 |
| D-11 | Runtime footprint | A third JVM on the 2 GB VPS (security-api + hub-api + finance-api at 768 MB each) likely doesn't fit. Either lower the per-service limit/heap for `hub-api` and `finance-api`, or resize the host. Measure in Phase 7. | PROPOSED | Phase 7 |
| D-12 | FX history retention | Keep every rate used by a transaction (rates are stored on the row anyway). Keep a daily snapshot of `finance_exchange_rates` indefinitely for back-dated recurring. Prune intra-day duplicates older than 90 days. Add the index. | PROPOSED | Phase 3 |
| D-13 | Budget status thresholds | One rule everywhere: the integer `HALF_UP` usage % drives both UI status and alert level. Luyra uses the rounded value for UI and the unrounded one for Telegram. | PROPOSED | Phase 3, 6 |
| D-14 | Neon after cutover | Read-only for 30 days, then a `pg_dump` archive kept off-repo, and then the finance tables are dropped from Neon (owner's action). | PROPOSED | Phase 7 |
| D-15 | `PATCH /transactions/{id}` semantics | Keep full-replace for compatibility and document it in Swagger. A true partial PATCH can come later under a new path. | PROPOSED | Phase 1b |
| D-16 | Unknown JSON fields | Luyra's zod silently strips them; H-Space's vault rejects them. For finance, **reject** (`FAIL_ON_UNKNOWN_PROPERTIES` on finance request types) and test that the Luyra UI sends no extra fields. | PROPOSED | Phase 1 |

## Risk register

Abbreviations: L = likelihood, I = impact, H = high, M = medium, L = low.

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| R1 | **Cross-user data access**: no RLS, and the source doesn't check referenced ids for ownership | M | H | Ownership checks in the service for every referenced id; composite FKs `(x_id, user_id)`; other-user tests on every endpoint; a pre-check that fails the import on cross-owner references |
| R2 | UUID mismatch between Luyra and H-Space identities | M | H | D-3 mapping table, approved by the owner; never email-only; stop on any unmapped owner |
| R3 | Duplicate budgets (or subscription statuses or aliases) already in the source | M | M | Phase-0 count; unique constraints in the destination; the import aborts on duplicates, and the owner resolves them in the source |
| R4 | Fractional KRW or a null description in the source breaks the new constraints | L | M | Phase-0 counts; stop and decide; never silently round |
| R5 | Rounding drift (₩1) between Luyra and Spring | L | L | D-7; golden tests; reconciliation compares *stored* values, which are copied, not recomputed |
| R6 | Both cron producers run, causing duplicate recurring rows or double notifications | M | H | Jobs off by default; Vercel crons removed at the freeze; advisory lock; partial unique index; per-channel markers |
| R7 | A partial write (contribution without the balance update) | L | M | One `@Transactional` method; a rollback test |
| R8 | Concurrent edits clobber each other | M | M | `@Version` → 409; `ON CONFLICT` upserts; atomic increments |
| R9 | Month/day boundaries shift (UTC vs Seoul) | M | M | Injected Seoul `Clock`; boundary tests; known UTC spots fixed (DATABASE-SCHEMA §6) |
| R10 | Error responses leak SQL or constraint text (`GlobalExceptionHandler` catch-all returns `ex.getMessage()`) | H | M | Task 0.5, a separate `security-api` PR, before finance endpoints ship. **Done in #29**, awaiting merge |
| R11 | Frontend contract drift (201→200, 422→400, envelope) | M | M | Client adapter + shared JSON snapshots; Vitest in Luyra |
| R12 | Memory exhaustion on the VPS with a third JVM | M | H | D-11; measure before production |
| R13 | Leaking production detail into the public repo (Neon ids, live schema dumps, row counts, exports) | M | H | Keep them off-repo (README); **the untracked `Luyra_to_HSpace_Finance_Architecture_Review.md` in the repo root contains Neon project/branch ids and live-schema detail — don't commit it** |
| R14 | An AI suggestion becomes a stored budget without scrutiny | L | M | Apply goes through normal validation; respect `ai_coach_enabled`; audit the `source` |
| R15 | Rollback after cutover loses Spring-side writes | L | H | Ids preserved; a reverse copy rehearsed on a Neon branch; a deletion log |
| R16 | Plan-of-record drift (`spring-migration-plan.md` vs this folder) | M | L | D-1 recorded and the plan's open item struck through; keep `docs/finance-api.md` as the short status page |

## Contradictions found

| # | Contradiction | Resolution |
|---|---|---|
| C1 | `docs/finance-api.md` said finance data is in Supabase under RLS | It's Luyra Neon, without RLS. `finance-api.md` updated to point here |
| C2 | `docs/spring-migration-plan.md`: finance would become a `hub-api` package | Superseded by D-1 |
| C3 | `luyra:docs/ARCHITECTURE.md` says finance uses its own Supabase project | Stale; `APP-MIGRATION.md` and the code say Neon |
| C4 | `luyra:docs/FINANCE-GAP-ANALYSIS.md`: "email manual-only", "categories GET-only", "no push", "no service tests" | All stale against the code |
| C5 | `luyra:docs/DEPLOYMENT.md` cron table vs `vercel.json` | `vercel.json` wins |
| C6 | `finance-api` prod pool 20 vs `docs/database.md` (5) vs `hub-api` (3) | **Resolved** in #27: aligned with `hub-api` (3, min-idle 1) |
