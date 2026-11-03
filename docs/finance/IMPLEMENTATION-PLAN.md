# Implementation plan

Rules for every phase:

- One PR per slice, on a feature branch.
- `./gradlew clean build` passes (JDK 17–21).
- Tests run against real Postgres (`finance_api_test`) with **synthetic
  data only**.
- Docs are updated in this folder and in `docs/finance-api.md`.
- No production database is touched outside the Phase 7 runbook.
- **Nothing in Phases 1–7 starts without the owner's explicit approval of
  that phase.**

Migration files go in `finance-api/src/main/resources/db/finance-migration/`
(history table `flyway_finance_schema_history`). Planned versions:

| Version | Content | Phase |
|---|---|---|
| V1 | `finance_categories`, `finance_payment_methods` (+ `updated_at`, `version`, `UNIQUE(user_id, lower(name))`, `UNIQUE(id, user_id)`) | 1 |
| V2 | `finance_recurring_transactions` (table only), `finance_transactions` (composite FKs, CHECKs, `request_id`, partial unique `(recurring_id, date)`) | 1 |
| V3 | `finance_budgets` (unique), `finance_savings_goals`, `finance_savings_contributions`, `finance_preferences`, `finance_exchange_rates` (+ index) | 3 |
| V4 | `finance_transaction_templates`, `finance_subscription_status`, `finance_transaction_description_aliases` (uniques) | 5 |
| V5 | Notification tables, if D-10 moves them here | 6 |

---

## Phase 0 — Architecture, schema and contract verification

**Objective.** Close every BLOCKED item before writing code.

| Task | Who | Output |
|---|---|---|
| 0.1 Review these docs | Owner | Approved, or changes requested |
| 0.2 Live-schema metadata check on Luyra Neon (read-only catalog queries: `pg_constraint`, `pg_indexes`, `pg_class.relrowsecurity`, `information_schema.columns`) plus **aggregate-only** data-quality counts (DATABASE-SCHEMA §5 pre-checks) | Owner, or Claude with approval | Diff vs DATABASE-SCHEMA.md, kept off-repo; this doc is corrected where it's wrong |
| 0.3 Identity check (AUTH-AND-DATA-MIGRATION §2) | Owner | Mapping table, kept off-repo |
| 0.4 Answer decisions D-2…D-14 | Owner | RISKS-AND-DECISIONS.md updated |
| 0.5 `security-api` `GlobalExceptionHandler`: handle `MethodArgumentTypeMismatchException`, `ConstraintViolationException`, `DataIntegrityViolationException`; stop echoing `ex.getMessage()` in the catch-all (gap G5) | Separate PR | Tests in `security-api` |

**Definition of Done**

- No open BLOCKED items for Phase 1. The open ones are D-2, D-3, D-5 and
  D-7.
- The schema doc matches the live catalog.

---

## Phase 1 — Module groundwork, categories, payment methods, transaction CRUD

**Objective.** The first vertical slices: ownership, money and Flyway
patterns, proven end to end.

Phase 1 is split into **two PRs**.

**1a — Groundwork + lookups** (the [recommended first task](#recommended-first-task))

**1b — Transaction CRUD**

> **Status 2026-11-03: built on branch `feat/finance-transactions`, which
> is stacked on `feat/finance-lookups`. Not committed yet.**
> `./gradlew clean build` is green: finance-api has 88 tests and the full
> build has 305, all passing.
>
> What was applied:
>
> - D-7 as proposed: whole-won KRW, `HALF_UP`, ₩1 parity case pinned.
> - D-15: full-replace PATCH.
>
> What was deferred:
>
> - D-8 (`Idempotency-Key`): it needs a decision, and V3 can add the
>   column.
> - A concurrent-PATCH `@Version` test.
>
> The category/payment-method "still in use" delete check from 1a's
> deferral is done.

- **Files:**
  - `domain/FinanceTransaction`, `domain/money/KrwConverter`
  - `repository/FinanceTransactionRepository`
  - `service/TransactionService(+Impl)`
  - `controller/TransactionController`
  - `payload/transaction/*`
  - `V2__finance_transactions.sql`
- **Endpoints:** API-CONTRACT #10–13 (list with the full filter/sort/page
  contract, create, full-replace PATCH, delete).
- **Subtasks:**
  - `KrwConverter` as a pure function
  - list query: JPQL/Criteria or native, always `user_id` first; escape
    `%`/`_`; stable tiebreak `created_at desc, id desc`
  - `hasMore` computed from 21 rows
  - ownership and type checks on referenced ids
  - `Idempotency-Key`, if D-8 is accepted
- **Tests:**
  - KRW create; USD create where the client KRW is ignored
  - rounding golden cases (`.5` boundary, large values)
  - invalid currency combinations → 400
  - another user's category/payment method → 404
  - another user's transaction get/patch/delete → 404
  - list filters each on their own, half-open dates (row on `end` excluded)
  - pagination 20/21, sort stability with equal dates
  - search escaping
  - non-UUID id → 400
  - 401 without a token
  - concurrent PATCH → 409 (`@Version`)
  - idempotent replay
- **Acceptance:**
  - JSON field set equals Luyra's `Transaction`
  - amounts serialize as numbers
  - all tests green under `clean build`
- **Risks:** list query performance (index `(user_id, date)`); the
  full-replace PATCH surprises future clients (documented in Swagger).

**DoD for Phase 1:**

- 1a and 1b merged
- `docs/finance-api.md` and this plan updated
- Swagger shows the `finance` group

---

## Phase 2 — Filtering, bulk operations, suggestions, validation hardening

- **Endpoints:** #14 bulk (delete/recategorize), #15 suggestions.
- **Subtasks:**
  - bulk in one transaction, ≤200 unique ids, counts only the caller's rows
  - recategorize checks category ownership and type compatibility per row
  - suggestions: 365-day Seoul window, prefix weighting ×3, latest-use
    category/payment method, max 6
- **Tests:**
  - bulk with a mix of own and foreign ids (foreign ones are not affected
    and are not counted)
  - duplicate ids → 400; >200 → 400
  - type mismatch → 400
  - suggestions: ranking golden cases ported from Luyra behaviour, caller
    scope only
- **DoD:** parity with the Luyra UI flows for the transactions page.

## Phase 3 — Budgets, savings, preferences, exchange rate

- **Migration:** V3.
- **Endpoints:** #9, #23–31, #37–38.
- **Subtasks:**
  - budget upsert via `ON CONFLICT`, plus ownership and expense/both checks
  - budget performance (rounded-pct status, D-13)
  - savings goals: opening balance kept; contributions run insert +
    increment in one transaction; Seoul month
  - preferences: defaults when there's no row; `ON CONFLICT (user_id)`
    upsert; cross-field checks against stored values
  - FX service: DB cache 60 min, 3 s timeout (`RestClient`), fallback to
    the last stored rate, then 1370; provider key from env only
- **Tests:**
  - **parallel budget PUTs → exactly one row**
  - contribution failure mid-transaction → balance unchanged
  - parallel contributions → balance = opening + Σ
  - contribution at 00:30 KST on the 1st → current month (fixed `Clock`)
  - another user's goal → 404
  - preference rule violations → 400
  - FX: provider down → fallback flagged (provider stubbed with a local
    HTTP stub, no real network)
- **DoD:** budgets and savings screens work against Spring in a local run.

## Phase 4 — Overview, analytics, review/plan, reports, export

- **Endpoints:** #39–44, #49.
- **Subtasks:**
  - `FinanceReportQueries` (JdbcTemplate, user-scoped `SUM`/`GROUP BY`)
  - pure ports of `financial-health`, `spending-class`, `budget-plan`
    (median/mean, rounding steps), review observations, weekly/monthly
    windows
  - plan apply rebuilds the plan on the server, 409 on drift, one
    transaction
  - export streams CSV/JSON with formula-injection escaping (envelope
    exception, D-6)
- **Tests:**
  - **report totals equal the sum of ledger rows** for synthetic months,
    including year and Seoul month boundaries
  - golden tests ported from the Luyra Vitest suites (`budget-plan`,
    `financial-health`, `spending-class`)
  - plan drift → 409
  - export contains only the caller's rows and escapes `=+-@`
- **DoD:** overview, analytics, review and reports totals match Luyra for
  the same synthetic data set (cross-implementation parity script run
  locally).

## Phase 5 — Recurring, subscriptions, templates, merchant names

- **Migration:** V4.
- **Endpoints:** #16–22, #32–36.
- **Subtasks:**
  - `RecurringSchedule` pure port (anchor day, clamp, 62/run, 92-day
    limit)
  - apply-due runs per rule in its own transaction with the guarded
    `next_date` update
  - `RecurringJob` behind `finance.jobs.enabled` with an advisory lock
  - subscription detection and merchant normalization as pure functions
  - template cap under a lock
  - alias merge in one transaction
- **Tests:**
  - port `recurring-schedule.test.ts` cases (31st→Feb→Mar, leap years)
  - **a job run twice (and concurrently) creates no duplicate
    occurrences**
  - a USD occurrence uses the historical rate
  - a save applies all of the user's due rules
  - template cap race → still ≤12
  - subscription and merchant golden cases
- **DoD:** recurring parity on synthetic schedules; job is off by default.

## Phase 6 — Notifications, scheduled reports, optional AI

**Depends on D-10:** who owns Telegram and push, Luyra or H-Space phase
2b.

- **Subtasks:**
  - budget alerts (one level rule, D-13), spending spike, reminders,
    weekly/monthly reports with per-channel markers
  - Telegram and SMTP clients configured from env
  - money coach: Anthropic, respects `ai_coach_enabled`, rate-limited,
    never persists numbers; the latest Claude model id comes from config
- **Tests:**
  - markers make a re-run send nothing
  - quiet hours, evaluated in Seoul
  - delivery failure → no marker (at-least-once documented)
  - the coach is not called when disabled; an AI recommendation for an
    unknown or non-flagged category is dropped
- **DoD:** every Vercel finance cron has a Spring replacement, still
  disabled.

## Phase 7 — Integration, reconciliation, production migration, deployment

- **Subtasks:**
  - CI image matrix entry for `finance-api` and its smoke check
  - compose service (profile `finance`), Caddy route `/api/finance/*`,
    memory limits (D-11)
  - Luyra Spring adapter + H-Space login (Luyra repo, its own PR and tests)
  - rehearsal on a Neon branch, then the production runbook
    (AUTH-AND-DATA-MIGRATION §4–8)
- **Acceptance:**
  - reconciliation identical
  - smoke test passes
  - rollback rehearsed
  - jobs enabled one at a time
- **DoD:**
  - Luyra finance on Spring for 30 days without a rollback
  - Neon finance tables archived (D-14)
  - `docs/finance-api.md` reflects production

---

## Recommended first task

> **Status 2026-11-03: built on branch `feat/finance-lookups`, not
> committed yet.** `./gradlew clean build` is green: finance-api has 36
> tests and the full build has 253, all passing.
>
> Deviations from the plan below:
>
> - Delete does no usage check yet, because no referencing tables exist.
>   It is added in 1b.
> - Non-UUID path ids still answer 500 until task 0.5 lands.

**Phase 1a: groundwork + categories/payment methods**, as one PR, after
Phase 0's D-2, D-3 and D-5 are answered. Do task 0.5 first, as its own
PR, if you want 400 rather than 500 for non-UUID ids in this slice's
tests.

**Scope**

1. **`build.gradle`:** add `spring-security-oauth2-core` to
   `project(':finance-api')`.
2. **`config/FinanceFlywayConfig`:** a copy of `HubFlywayConfig` with
   location `db/finance-migration`, table `flyway_finance_schema_history`,
   and the `finance.flyway.run-security-migrations` property.
3. **`application-prod.yml`:** aligned with `hub-api` (pool 3/1,
   `sslmode=require`, docs gate, security migrations off).
4. **`V1__finance_lookups.sql`:** `finance_categories`,
   `finance_payment_methods`. `user_id uuid not null references users(id)
   on delete cascade`; `UNIQUE (id, user_id)` for later composite FKs;
   `UNIQUE (user_id, lower(name))`; `type NOT NULL` with its CHECK;
   `spending_class` CHECK; `created_at`/`updated_at`/`version`.
5. **Code:**
   - entities `FinanceCategory`, `FinancePaymentMethod` (extend
     `BaseEntity`)
   - repositories with `findAllByUserIdOrderByNameAsc`,
     `findByIdAndUserId`, `existsByUserIdAndNameIgnoreCase…`
   - services with a 409 on a duplicate name (a pre-check plus catching
     the unique-index violation for the race)
   - usage-count + delete in one transaction (409 if in use; until V2
     exists, usage is 0)
   - controllers for API-CONTRACT #1–8
   - Lombok request classes with Bean Validation; record responses
6. **Tests (`CategoryControllerIntegrationTest`,
   `PaymentMethodControllerIntegrationTest`).** Two synthetic users and
   JWTs from `JwtTokenProvider`, like `InboxItemControllerIntegrationTest`.
   - list sorted by name, caller only
   - create; case-insensitive duplicate → 409
   - invalid color/type/class/length → 400
   - patch, including ≥1 key → 400
   - another user's id → 404 on patch/delete
   - delete
   - no token → 401
   - Flyway: the finance history table exists and `security-api`'s history
     is untouched
   - replace the fictional `/api/finance/v1/accounts` probe with
     `/api/finance/categories`

**Acceptance**

- `./gradlew clean build` is green.
- The response field sets equal Luyra's `Category`/`PaymentMethod`.
- No Luyra, Neon or production change.

**Why this first**

- It has no money arithmetic, yet it exercises every cross-cutting
  pattern:
  - own Flyway history
  - JWT principal
  - ownership 404
  - unique-constraint 409
  - envelope
  - test fixtures
- Transactions (1b) depend on these tables for composite FKs.
