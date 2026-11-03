# Finance database schema (Luyra Neon)

**Source.** This schema is derived from `luyra:db/migrations/*.sql` at
`f63fcb8`, so it is **UNVERIFIED against the live database**.

The migrations were applied by hand: the repo has no runner, and two
files both use version `010_`. Three of them also rebuild constraints by
pattern-matching names:

- 007 looks the FK up by `conname like '%goal_id%'`
- 014 re-creates the `recurring_id` FK *only if one was found*
- 014 drops "a" check that contains `amount_usd >`

An earlier read-only live review (2026-11-03) reported several things
that are **INFERRED** here, because they were not re-checked:

- 14 `finance_*` tables in `public`
- RLS disabled on all of them
- the partial unique `(recurring_id, date)` index exists
- budgets have no unique `(user_id, category_id)`

Phase 0 task 0.2 ([IMPLEMENTATION-PLAN](IMPLEMENTATION-PLAN.md)) closes
this gap by reading catalog metadata only.

The same migration folder also creates `users` (001), `telegram_accounts`
(008) and `push_subscriptions` (010). Finance depends on them.

## 1. Conventions in the source schema

- Every `user_id` is `uuid not null references users(id) on delete cascade`.
- Primary keys are `uuid default gen_random_uuid()`, except where noted.
- Money is unconstrained `numeric`, with no precision or scale.
- Business dates are `date`. Audit timestamps are `timestamptz`. Month
  keys are `text`/`char(7)` in the form `YYYY-MM`.
- Category and payment-method FKs are **plain `(id)` with NO ACTION**.
  They prove the row exists, not that it belongs to the same user.

## 2. Table inventory (14 finance tables + 3 dependencies)

| # | Table | PK | Owner column | Purpose |
|---|---|---|---|---|
| 1 | `finance_transactions` | id | user_id | Income/expense ledger; `amount_krw` is canonical |
| 2 | `finance_categories` | id | user_id | User categories; `type`, `spending_class` |
| 3 | `finance_payment_methods` | id | user_id | Payment labels |
| 4 | `finance_budgets` | id | user_id | Monthly KRW budget per category plus Telegram alert state |
| 5 | `finance_savings_goals` | id | user_id | USD goals; `current_usd` is a running total |
| 6 | `finance_savings_contributions` | id | user_id | Contribution history (`goal_id` cascade) |
| 7 | `finance_recurring_transactions` | id | user_id | Recurring rules |
| 8 | `finance_transaction_templates` | id | user_id | Quick-add presets (≤12/user, enforced in app) |
| 9 | `finance_preferences` | **user_id** | user_id | Per-user settings, report markers |
| 10 | `finance_exchange_rates` | id | — (global) | USD→KRW quote cache |
| 11 | `finance_ai_insights` | id | user_id | **Unused** |
| 12 | `finance_subscription_status` | id | user_id | User decision per detected subscription key |
| 13 | `finance_transaction_description_aliases` | id | user_id | Merchant-name merges |
| 14 | `finance_push_budget_alerts` | (user_id, category_id, month_key) | user_id | Push dedupe |
| — | `users` | id (= Supabase `sub`) | — | Mirror of the Supabase identity |
| — | `telegram_accounts` | user_id | user_id | Telegram link (used by report and alert jobs) |
| — | `push_subscriptions` | id | user_id | Web-push endpoints (`endpoint` unique) |

## 3. Columns, constraints and findings

### 3.1 `finance_transactions` (004, 014)

**Columns**

| Column | Type | Null | Default / check |
|---|---|---|---|
| id | uuid | no | gen_random_uuid() |
| user_id | uuid | no | FK users cascade |
| recurring_id | uuid | yes | FK recurring **SET NULL** (014, conditional) |
| date | date | no | |
| type | varchar | no | CHECK income/expense |
| category_id | uuid | yes | FK categories, NO ACTION |
| description | varchar | **yes** | — (app requires 1..200) |
| amount_krw | numeric | no | **no CHECK > 0** |
| amount_usd | numeric | yes | holds the *original* USD amount |
| exchange_rate | numeric | yes | USD→KRW |
| payment_method_id | uuid | yes | FK payment methods, NO ACTION |
| note | text | yes | |
| currency | text | no | 'KRW', **no CHECK** |
| created_at, updated_at | timestamptz | no | now() |

**Indexes**

- `(user_id)`
- `(user_id, date)`
- **partial unique** `finance_transactions_recurring_occurrence_idx (recurring_id, date) WHERE recurring_id IS NOT NULL`

**Findings**

- `description` is nullable while the app requires it.
- There is no positivity check on `amount_krw` and no currency check.
- No rule ties `currency='USD'` to non-null `amount_usd`/`exchange_rate`,
  or `currency='KRW'` to null ones.
- Fractional KRW values are possible: the API accepts them and only the
  client strips decimals (**INFERRED**).

### 3.2 `finance_categories` (004, 013)

**Columns**

| Column | Type | Null | Default / check |
|---|---|---|---|
| id, user_id | | | (convention) |
| name | varchar | no | |
| icon | varchar | yes | |
| color | varchar | yes | '#10b981' |
| type | varchar | **yes** | 'expense', CHECK income/expense/both |
| spending_class | text | yes | CHECK essential/commitment/growth/flexible/avoidable |
| created_at | timestamptz | no | |

Index on `(user_id)`.

**Findings**

- There is **no unique name per user**. Case-insensitive uniqueness is
  enforced only in the app, as a pre-query, so it can race.
- `type` is nullable.
- There is no `updated_at`.
- 013 is a data-only backfill of `spending_class`.

### 3.3 `finance_payment_methods` (004)

Columns: id, user_id, `name varchar not null`, icon, created_at.

**Finding:** there's no unique name per user; uniqueness is enforced in
the app only.

### 3.4 `finance_budgets` (004)

**Columns**

| Column | Type | Null | Default |
|---|---|---|---|
| id, user_id | | | |
| category_id | uuid | no | FK categories, NO ACTION |
| amount_krw | numeric | no | 0 |
| alert_month | text | yes | (YYYY-MM, no CHECK) |
| alert_level | int | no | 0 |
| created_at, updated_at | timestamptz | yes | now() |

Index on `(user_id)` only.

**Findings: no unique `(user_id, category_id)`**

- `upsertBudget` (`luyra:lib/repositories/finance-budget-repository.ts:29-48`)
  runs `SELECT`, then `UPDATE … WHERE user_id AND category_id` (all
  matches) or `INSERT`. That's three autocommit statements.
- Two concurrent saves can therefore create duplicate rows.
  `findBudgetsByUser` returns both, and totals double-count them
  (**INFERRED**).
- The batch path (plan apply) is atomic but still not race-proof under
  READ COMMITTED.
- A budget's `category_id` isn't checked for ownership, or for being an
  expense/both category.
- Live duplicates must be counted before any unique index is added
  (Phase 0 task 0.2).

### 3.5 `finance_savings_goals` (004)

**Columns**

- name, icon, color: text, not null, with defaults
- target_usd, current_usd: numeric, not null, default 0
- deadline: date
- note: text
- purpose: text, **no CHECK** (the app's enum is
  emergency_fund/sinking_fund/goal/investment/other)
- auto_monthly_usd (default 0), last_auto_month, reminder_day (1..28)
- skipped_month, last_reminder_month, last_contribution_month (text,
  YYYY-MM CHECK)
- created_at, updated_at

**Findings**

- The six auto-plan columns are **schema only**; nothing reads them.
- `current_usd` is a running total and is **not** equal to
  Σcontributions: a goal can be created with an opening balance, and the
  data was migrated from Money Flow.

### 3.6 `finance_savings_contributions` (004, 007)

**Columns**

- goal_id: FK goals **ON DELETE CASCADE** (007; production confirmed per
  `luyra:docs/FINANCE-GAP-ANALYSIS.md`)
- amount_usd: numeric, not null, CHECK > 0
- contribution_month: text, not null, YYYY-MM CHECK
- source: text, not null, CHECK manual/planned (only `manual` is written)
- created_at

Index on `(user_id)` only.

**Findings**

- There is **no index on `goal_id`**.
- Inserting a contribution and incrementing `current_usd` are two
  autocommit statements.
- The contribution month is computed in **UTC** (see §6).

### 3.7 `finance_recurring_transactions` (004, 014)

**Columns**

| Column | Type | Notes |
|---|---|---|
| type | text | not null, CHECK income/expense |
| amount_krw | numeric | not null, CHECK > 0 |
| amount_usd | numeric | null allowed since 014, CHECK null or > 0 |
| description | text | not null |
| category_id, payment_method_id | uuid | FKs, NO ACTION |
| note | text | |
| frequency | text | not null, CHECK daily/weekly/monthly/yearly |
| next_date | date | not null |
| last_applied | date | |
| exchange_rate | numeric | null allowed since 014, default dropped |
| currency | text | not null, default KRW, CHECK KRW/USD |
| is_active | bool | not null, default true |
| anchor_day | smallint | not null, CHECK 1..31 (014, backfilled) |
| created_at | timestamptz | |

Indexes: `(user_id)`, partial `(next_date) WHERE is_active`.

**Findings:** there is no `updated_at`. Idempotency relies on the
`finance_transactions` partial unique index plus a guarded
`next_date` update.

### 3.8 `finance_transaction_templates` (004)

Columns: type, description (not null), amount_krw (> 0), category_id,
payment_method_id, note, `currency` (never written), created_at.

**Finding:** the ≤12 cap is a `count(*)` followed by an insert, so it can
race.

### 3.9 `finance_preferences` (004, 009–012)

PK `user_id`.

**Columns**

- monthly_spending_limit_krw
- target_savings_rate (0..100, default 20)
- essential_target_pct (0..100, default 50, from 012)
- budget_warning_thresholds: jsonb `{first:70, strong:90, over:100}`
- quiet_hours: jsonb
- monthly_review_enabled, weekly_review_enabled
- ai_coach_enabled, share_descriptions_with_ai
- report channel flags (telegram, email), finance_report_email
- four "last sent" markers (Telegram and email × weekly and monthly)
- timestamps

**Findings**

- There is no DB check that `essential + savings ≤ 100`.
- `ai_coach_enabled` and `share_descriptions_with_ai` are **ignored** by
  the coach.
- `monthly_spending_limit_krw` is stored but never used in server logic.
- The upsert checks first and then writes, even though the PK would allow
  `ON CONFLICT`.

### 3.10 `finance_exchange_rates` (004)

Columns:

- base_currency: default `'KRW'`
- target_currency: default `'USD'`
- rate
- fetched_at: nullable

**Findings**

- The code always writes `('USD','KRW')`, the reverse of the column
  defaults.
- There is **no index**: every lookup scans with
  `ORDER BY fetched_at DESC LIMIT 1`.
- There is no retention, so rows grow forever.
- The table is global; there is no user FK.

### 3.11 `finance_ai_insights` (004)

Enum CHECKs, an evidence jsonb, and snooze/expiry columns. **Unused.**
Recommendation: don't port it (decision D-9).

### 3.12 `finance_subscription_status` (004)

Columns:

- subscription_key: text, not null
- display_name: text, not null
- note
- status: CHECK keep/review/plan_to_cancel/cancelled, default review
- timestamps

**Findings**

- There is **no unique `(user_id, subscription_key)`**; the upsert checks
  first and then writes.
- The key is a normalized merchant string with no FK.

### 3.13 `finance_transaction_description_aliases` (004)

Columns: canonical_description, variant_description, normalized_key
(all not null), created_at.

**Finding:** there is **no unique `(user_id, normalized_key)`**. The
merge itself is atomic (delete + insert run in one batch).

### 3.14 `finance_push_budget_alerts` (010_push_notifications)

PK `(user_id, category_id, month_key)`. `category_id` is FK
**ON DELETE CASCADE**, the only category FK with cascade. The
`last_status` CHECK allows watch/near_limit/exceeded. Index on
`(user_id, month_key)`.

## 4. FK delete behaviour

| FK | On delete |
|---|---|
| `*.user_id → users` | CASCADE |
| transactions.category_id / payment_method_id | NO ACTION (the app returns 409 "still used by N…") |
| transactions.recurring_id | SET NULL (014) |
| recurring, templates: category/payment method | NO ACTION |
| budgets.category_id | NO ACTION |
| savings_contributions.goal_id | CASCADE |
| push_budget_alerts.category_id | CASCADE |

## 5. Constraints the code assumes but the database doesn't enforce

These are **PROPOSED** as destination Flyway constraints. Each one is
added only after a pre-check on the source data:

| Invariant | Proposed constraint | Pre-check on source |
|---|---|---|
| One budget per category | `UNIQUE (user_id, category_id)` on budgets | `group by user_id, category_id having count(*)>1` |
| One status per subscription key | `UNIQUE (user_id, subscription_key)` | same pattern |
| One alias per key | `UNIQUE (user_id, normalized_key)` | same pattern |
| Category and payment-method names unique per user, case-insensitive | `UNIQUE (user_id, lower(name))` index (**built**, V1) | same pattern |
| Lookup values within the API's limits | **Built** (V1): name 1..40 chars (trimmed), icon 1..8, color `#RRGGBB` | `count(*) where char_length(btrim(name)) not between 1 and 40`, `… icon …`, `color !~ '^#[0-9a-fA-F]{6}$'` |
| Referenced category/payment method/rule/goal belong to the same user | `UNIQUE (id, user_id)` on parents + composite FKs `(category_id, user_id) → categories(id, user_id)`. **Built** for categories, payment methods and recurring rules (V1, V2); goals come with V3 | join child to parent where `parent.user_id <> child.user_id` must return 0 |
| Positive amounts | `CHECK (amount_krw > 0)` on transactions (**built**, V2) | `count(*) where amount_krw <= 0` |
| Whole won | **Built** (V2): `NUMERIC(14,0)`. Postgres *rounds* a fractional value into this column instead of rejecting it, so the API rejects fractional KRW (`KrwConverter`) and an import **must** run the pre-check | `count(*) where amount_krw <> trunc(amount_krw)` (decision D-7) |
| Currency shape | **Built** (V2): `CHECK (currency in ('KRW','USD'))` + a currency-shape check (USD carries amount and rate, KRW neither) | count violations |
| Description present | `description NOT NULL` (**built**, V2) | `count(*) where description is null` |
| Category type present | `type NOT NULL` (**built**, V1) | count nulls |
| Preferences rule | `CHECK (essential_target_pct + target_savings_rate <= 100)` | count violations |
| FX lookup | index `(base_currency, target_currency, fetched_at desc)` | — |
| Contributions by goal | index `(goal_id)` | — |
| Template cap | stays in the service, under `SELECT … FOR UPDATE` on the user's preferences row (or an advisory lock) | — |

If a pre-check finds violations, **stop and report them**. Never clean
production rows silently (see AUTH-AND-DATA-MIGRATION §5).

## 6. Dates and time zones

**VERIFIED.** The app zone is Asia/Seoul. Months are half-open
`[YYYY-MM-01, next-01)`, and the interactive UI sends `?month=` from the
browser clock. Places that are *not* Seoul-pinned (port them
deliberately):

| Where | Behaviour | Proposed fix |
|---|---|---|
| Savings contribution month (`luyra:…/finance-savings-repository.ts:152`) | UTC `toISOString().slice(0,7)`: 00:00–08:59 KST on the 1st lands in the previous month | `YearMonth.now(SEOUL)` |
| push-budget-alerts month | UTC | Seoul |
| Subscription lookback | Server local time, then UTC | `LocalDate.now(SEOUL).minusMonths(6)` |
| Description suggestions `current_date - 365` | DB session zone | Compute in Java (Seoul), pass as a parameter |

## 7. Database security assumptions

- **No RLS** on the finance tables (**INFERRED**, from the live review).
  The application's `user_id` filter is the only tenant boundary.
- Joins from transactions/budgets/templates/recurring to categories and
  payment methods match only on `id`, not on `user_id`. If a user attaches
  a foreign category UUID, the join can read that category's name, icon,
  color and class (**VERIFIED** code; exploitation needs the UUID).
- Global/cron queries without a user filter are intentional:
  - due recurring rules
  - report recipients
  - Telegram lookups
  - FX
- `updateBudgetAlertState` updates `where id = $` without `user_id`. It
  isn't reachable from outside, but the port must scope it anyway.
- The Neon role the app uses appears to have full DML on the shared
  database, which also holds learning, task and habit tables
  (**INFERRED**). The destination should use a least-privilege role.
