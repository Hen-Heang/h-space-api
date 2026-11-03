# API contract and migration mapping

Source of the existing contract (**VERIFIED**):

- `luyra:app/api/finance/**`
- `lib/api/finance.ts`
- `lib/api/client.ts`
- `lib/validation/finance.ts`
- `types/finance.ts`

The Spring side is **PROPOSED**.

**Goal:** keep paths, methods, field names and JSON types the same, so
the only frontend change is the transport adapter in `lib/api/client.ts`.
That file's own header comment calls it "the single switch point".

## 1. Envelope (decision D-5)

| | Luyra today | H-Space `ApiResponse` |
|---|---|---|
| Success | `{"data": T}`, 200/201 | `{"status":{"code":200,"message":"Success"},"data": T}`, always HTTP 200 (`BaseController`) |
| Validation | 422 `{"error":{"code":"VALIDATION_ERROR","message":"<first issue>"}}` | 400 `{"status":{"code":400,"message":"<all messages>"},"data":{"field":"msg"}}` |
| Not found | 404 `NOT_FOUND` | 404, `status.message` |
| Conflict | 409 `CONFLICT` | 409 via `BusinessException(StatusCode.CONFLICT, msg)`, or `OptimisticLockingFailureException` |
| Unauthenticated | 401 `UNAUTHORIZED` | 401 (`JwtAuthenticationEntryPoint`) |
| Server error | 500 `INTERNAL_ERROR` | 500 with a generic message only. The old catch-all echoed `ex.getMessage()`; fixed in #29 (gap G5) |

**Recommended (PROPOSED): keep `ApiResponse` and adapt in the client.**
`AGENTS.md` requires the envelope, and `hub-api`'s Inbox already set the
pattern. The Spring adapter in Luyra's client then:

1. On `response.ok`, returns `body.data`.
2. Otherwise maps the HTTP status to Luyra's `error.code`:
   - 400 → `VALIDATION_ERROR`
   - 401 → `UNAUTHORIZED`
   - 404 → `NOT_FOUND`
   - 409 → `CONFLICT`
   - anything else → `INTERNAL_ERROR`

   The message comes from `status.message`.
3. Sends `Authorization: Bearer <H-Space access token>`, and on a 401
   refreshes once and retries (H-Space's `hspaceRequest` pattern).

Two things the frontend's tests must pin:

- **201 → 200.** Creates return 200 under `BaseController`. Luyra's client
  only checks `response.ok`.
- **422 → 400.** The UI shows `message`; nothing branches on the number
  (**INFERRED**; confirm by grepping `status === 422` in the Luyra UI
  during Phase 1).

## 2. Common shapes

These must stay identical; they become the response records.

- `Category { id, name, icon|null, color|null, type, spendingClass|null }`
- `PaymentMethod { id, name, icon|null }`
- `Transaction { id, date, type, categoryId|null, categoryName|null, categoryIcon|null, categoryColor|null, description, amountKrw, currency, originalAmount|null, exchangeRate|null, paymentMethodId|null, paymentMethodName|null, note|null, createdAt, updatedAt }`
- `RecurringRule`, `TransactionTemplate`, `Budget`, `SavingsGoal`,
  `SavingsContribution`, `DetectedSubscription`, `FinancePreferences`, and
  the aggregate types: copy the field lists **verbatim** from
  `luyra:types/finance.ts:184-442`.

JSON types:

| Kind | Format | Java side |
|---|---|---|
| Money | JSON **number**, never a string | `BigDecimal` serialized as a number (Jackson default); add a test that pins it |
| Dates | `"YYYY-MM-DD"` | `LocalDate` |
| Months | `"YYYY-MM"` | `YearMonth` with a custom (de)serializer |
| Timestamps | ISO-8601 UTC with `Z` | `Instant` |

Ids are UUID strings, and `user_id` is never returned.

## 3. Endpoint mapping

**Phase column.** Phases match [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md).

**Spring notes.** Every endpoint also gets:

- 401 when there is no token
- 404 for another user's id
- 400 for a malformed body or a non-UUID path id. The non-UUID case
  needs #29 (G5); until then it is a 500.

**Built so far:** #1–8 (PR #27), #10–13 (PR #28), #14–15 (PR #30).

### 3.1 Lookups

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 1 | `GET /api/finance/categories` | — | `Category[]` sorted by name | | 1 |
| 2 | `POST /api/finance/categories` | `name` 1..40, `icon` ≤8\|null, `color` `#RRGGBB`\|null, `type` (default expense), `spendingClass`\|null | `Category` | 409 on a case-insensitive duplicate name, backed by a unique index | 1 |
| 3 | `PATCH /api/finance/categories/{id}` | Partial, ≥1 key | `Category` | 404, 409 | 1 |
| 4 | `DELETE /api/finance/categories/{id}` | — | `{id}` | 409 with usage counts; count and delete run in one transaction | 1 |
| 5–8 | `GET/POST/PATCH/DELETE /api/finance/payment-methods[/{id}]` | `name` 1..40, `icon` | `PaymentMethod` / `{id}` | Same pattern as categories | 1 |
| 9 | `GET /api/finance/exchange-rate` | — | `{rate, fetchedAt, cached, fallback}` | 60-minute DB cache, 3 s timeout, fallback: last stored rate, then 1370 | 3 |

### 3.2 Transactions

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 10 | `GET /api/finance/transactions` | `start`, `end` (exclusive), `type?`, `categoryId?`, `paymentMethodId?`, `amountMin?`, `amountMax?`, `search?` ≤200, `sort?` (`date_desc` default), `page?` ≥0 | `{transactions, hasMore}` | Page size 20; fetch 21 rows to compute `hasMore`; tiebreak `created_at`, then `id`; escape `%`/`_` in search | 1 |
| 11 | `POST /api/finance/transactions` | `date`, `type`, `categoryId?`, `description` 1..200, `currency` KRW\|USD, `amountKrw` (KRW), `originalAmount` + `exchangeRate` (USD), `paymentMethodId?`, `note?` ≤500 | `Transaction` | Server computes KRW for USD; checks ownership and category type; optional `Idempotency-Key` | 1 |
| 12 | `PATCH /api/finance/transactions/{id}` | **Full schema** (Luyra's PATCH is a full replace) | `Transaction` | Keep full-replace semantics and document them; `@Version` | 1 |
| 13 | `DELETE /api/finance/transactions/{id}` | — | `{id}` | | 1 |
| 14 | `POST /api/finance/transactions/bulk` | `{action:"delete", ids}` or `{action:"recategorize", ids, categoryId\|null}`; 1..200 unique ids | `{affected}` | One transaction; only the caller's rows count | 2 |
| 15 | `GET /api/finance/transactions/suggestions` | `q` 1..100, `type?` | `DescriptionSuggestion[]` (max 6) | 365-day Seoul window passed in as a parameter | 2 |

### 3.3 Templates and recurring

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 16–18 | `GET/POST /api/finance/templates`, `DELETE /api/finance/templates/{id}` | `type`, `description`, `amountKrw` >0, `categoryId?`, `paymentMethodId?`, `note?` | `TransactionTemplate` / `{id}` | Max 12 (count + insert under a lock); 422 → 400 | 5 |
| 19 | `GET /api/finance/recurring` | — | `RecurringRule[]` | | 5 |
| 20 | `POST /api/finance/recurring` | `type`, `description`, `currency`, `amountKrw`\|`originalAmount`, `categoryId?`, `paymentMethodId?`, `note?`, `frequency`, `nextDate` (≤92 days in the past), `isActive` | `{rule, appliedCount}` | Saving applies **all** of the user's due rules (Luyra behaviour) in the same transaction | 5 |
| 21 | `PUT /api/finance/recurring/{id}` | Same as create | `{rule, appliedCount}` | Keeps `anchorDay` if `nextDate` is unchanged | 5 |
| 22 | `DELETE /api/finance/recurring/{id}` | — | `{id}` | Generated transactions keep their rows (`recurring_id` set to NULL) | 5 |

### 3.4 Budgets and savings

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 23 | `GET /api/finance/budgets?start&end` | — | `{categories, budgets, spendByCategory, performance}` | | 3 |
| 24 | `PUT /api/finance/budgets` | `{categoryId, amountKrw ≥0}` | `{categoryId}` | `ON CONFLICT` upsert; ownership and expense/both type check (new) | 3 |
| 25 | `DELETE /api/finance/budgets/{categoryId}` | — | `{categoryId}` | Keep 200 when nothing existed (Luyra behaviour) | 3 |
| 26–29 | `GET/POST /api/finance/savings`, `PATCH/DELETE /api/finance/savings/{id}` | `name` 1..80, `icon`, `color`, `targetUsd`, `currentUsd` (create only), `deadline?`, `note?`, `purpose?` | `SavingsGoal` / `{id}` | `currentUsd` can't be changed by PATCH | 3 |
| 30 | `GET /api/finance/savings/{id}/contributions` | — | `SavingsContribution[]` | 404 if the goal isn't the caller's | 3 |
| 31 | `POST /api/finance/savings/{id}/contributions` | `{amountUsd >0}` | `SavingsContribution` | Insert + balance increment in one transaction; Seoul month; `Idempotency-Key` | 3 |

### 3.5 Subscriptions and merchant names

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 32 | `GET /api/finance/subscriptions` | — | `DetectedSubscription[]` | 6-month Seoul lookback; port the detection as a pure function with golden tests | 5 |
| 33 | `PUT /api/finance/subscriptions/{key}` | `{displayName, status}` | `{key}` | URL-encoded key; `ON CONFLICT` upsert | 5 |
| 34–36 | `GET/POST/DELETE /api/finance/merchant-names` | POST `{name, keys[1..20]}`; DELETE **JSON body** `{name}` | see types | Spring accepts `@RequestBody` on DELETE; keep it | 5 |

### 3.6 Preferences, analytics and reports

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 37–38 | `GET/PUT /api/finance/preferences` | Partial update (see `updatePreferencesSchema`) | `FinancePreferences` (defaults when no row) | Check watch < nearLimit against stored values too (fixes a gap) | 3 |
| 39 | `GET /api/finance/overview?month=YYYY-MM` | — | `FinanceOverviewSummary` | Luyra's SSR prefetch calls the service directly; the frontend must switch it to HTTP or drop it (D-5) | 4 |
| 40 | `GET /api/finance/analytics?month=` | — | `AnalyticsSummary` | | 4 |
| 41 | `GET /api/finance/review?month=` | — | `ReviewSummary` | | 4 |
| 42 | `POST /api/finance/review/plan` | `{month, changes[1..100]}` | `{applied}` | Rebuild the plan on the server, 409 on drift, one transaction | 4 |
| 43–44 | `GET /api/finance/reports/weekly`, `GET /api/finance/reports/monthly?month=` | — | `WeeklySummary`, `MonthlyReport` | | 4 |

### 3.7 Delivery, AI and export

| # | Method & path | Request | Response `data` | Spring notes | Phase |
|---|---|---|---|---|---|
| 45 | `GET /api/finance/money-coach?month=` | — | `{ok:true, data:{summary, recommendation\|null}}` or `{ok:false, reason}` | A failure is data inside a 200 (keep). Respect `ai_coach_enabled`; rate-limit | 6 |
| 46–48 | `POST /api/finance/telegram/send`, `GET /api/finance/email/status`, `POST /api/finance/email/send` | `{type, month?, monthLabel?}` | `{sent, reason?}` / `{configured, recipientEmail}` | A failure is data inside a 200 (keep) | 6 |
| 49 | `GET /api/finance/export?format=csv\|json` | — | **Raw file download, no envelope** | Documented exception to the envelope rule (D-6); keep formula-injection escaping | 4 |

## 4. Out of scope for `finance-api`

These are Luyra-side or shared:

- `/api/telegram/*` (linking, webhook) and the web-push subscription
  routes: D-10.
- Learning, tasks, habits and goals routes, which are paused in Luyra.

## 5. Contract tests

- **Spring:** MockMvc integration tests assert the exact field set of each
  response record. Store JSON snapshots made from synthetic data under
  `finance-api/src/test/resources/contract/`.
- **Luyra:** Vitest tests for the Spring adapter (envelope unwrap, error
  mapping, 401 refresh-retry) plus a fixture test fed with the same
  snapshots.

## 6. Scheduled jobs (cron)

| Luyra path | Schedule (UTC) | Seoul | Spring replacement |
|---|---|---|---|
| `/api/cron/recurring` | `0 0 * * *` | 09:00 | `RecurringJob` |
| `/api/cron/budget-alerts` | `0 10 * * *` | 19:00 | `BudgetAlertJob` (Telegram) |
| `/api/cron/push-budget-alerts` | `0 10 * * *` | 19:00 | `BudgetAlertJob` (push) — D-10 |
| `/api/cron/spending-spike` | `0 12 * * *` | 21:00 | `SpendingSpikeJob` |
| `/api/cron/daily-reminder` | `0 3 * * *` | 12:00 | `DailyReminderJob` |
| `/api/cron/daily-log-reminder` | `0 11 * * *` | 20:00 | `DailyLogReminderJob` |
| `/api/cron/weekly-summary` | `0 0 * * 1` | Mon 09:00 | `WeeklySummaryJob` |
| `/api/cron/monthly-report` | `0 2 * * *` | 11:00 | `MonthlyReportJob` |

Spring jobs aren't HTTP endpoints, so no `CRON_SECRET` is needed on the
Spring side.

`luyra:docs/DEPLOYMENT.md`'s cron table is stale against `vercel.json`;
`vercel.json` wins.
