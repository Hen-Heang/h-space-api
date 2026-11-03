# Workflows

Each section shows the **existing** Luyra flow (VERIFIED from code) and,
where it differs, the **proposed** Spring flow (PROPOSED). The
architecture diagrams are in
[CURRENT-ARCHITECTURE.md](CURRENT-ARCHITECTURE.md) (existing) and
[SPRING-ARCHITECTURE.md](SPRING-ARCHITECTURE.md) (proposed).

## 1. Transaction creation

**Existing (Luyra)**

```mermaid
sequenceDiagram
  participant UI as Quick-add / Txn sheet
  participant R as POST /api/finance/transactions
  participant A as ensureAppUserId
  participant S as finance-transaction-service
  participant DB as Neon
  UI->>R: {date,type,currency,amountKrw | originalAmount+exchangeRate,...}
  R->>A: Supabase cookie
  A-->>R: userId (sub)
  R->>R: zod createTransactionSchema (422 on first issue)
  R->>S: addTransaction(userId, input)
  S->>S: USD: amountKrw = Math.round(original × rate)
  Note over S: categoryId / paymentMethodId NOT ownership-checked
  S->>DB: WITH t AS (INSERT … RETURNING *) SELECT … LEFT JOIN categories/methods
  DB-->>S: row (autocommit)
  S-->>R: Transaction
  R-->>UI: 201 {data}
```

**Proposed (Spring)**

```mermaid
sequenceDiagram
  participant C as Client adapter
  participant F as JWT filter (security-api)
  participant TC as TransactionController
  participant TS as TransactionServiceImpl
  participant Repo as Repositories
  participant DB as PostgreSQL
  C->>F: Bearer token, Idempotency-Key?
  F-->>TC: UserPrincipal (401 if invalid/revoked/unverified)
  TC->>TC: @Valid TransactionRequest (400 + field map)
  TC->>TS: create(userId, req, key)
  activate TS
  Note over TS: @Transactional
  TS->>Repo: findByUserIdAndRequestId(key) → replay returns existing
  TS->>Repo: category = findByIdAndUserId → 404; type compatible → 400
  TS->>Repo: paymentMethod = findByIdAndUserId → 404
  TS->>TS: KrwConverter: original.multiply(rate).setScale(0, HALF_UP)
  TS->>Repo: save(FinanceTransaction)
  Repo->>DB: INSERT (composite FKs, CHECKs, unique request_id)
  deactivate TS
  TS-->>TC: TransactionResponse
  TC-->>C: 200 ApiResponse{data}
```

## 2. Savings contribution

```mermaid
sequenceDiagram
  participant C as Client
  participant SS as SavingsServiceImpl
  participant DB as PostgreSQL
  Note over C,DB: Existing: ownership check, then INSERT and UPDATE as two autocommit statements, month in UTC
  C->>SS: POST /savings/{goalId}/contributions {amountUsd}, Idempotency-Key
  activate SS
  Note over SS: @Transactional (proposed)
  SS->>DB: SELECT goal WHERE id=:goalId AND user_id=:uid → 404
  SS->>DB: INSERT contribution (goal_id,user_id,amount_usd,month=YearMonth.now(Seoul),request_id)
  SS->>DB: UPDATE goals SET current_usd = current_usd + :amt, version=version+1 WHERE id AND user_id
  alt any statement fails
    SS-->>C: rollback both → 4xx/5xx, balance unchanged
  else
    SS-->>C: 200 {data: SavingsContribution}
  end
  deactivate SS
```

## 3. Recurring-transaction processing

```mermaid
flowchart TD
  T1["Trigger A: RecurringJob 09:00 Asia/Seoul<br/>(Luyra: Vercel cron 00:00 UTC)"] --> L{"pg_try_advisory_xact_lock(job)?<br/>(proposed)"}
  L -- no --> Skip[Another instance running → skip]
  L -- yes --> Q
  T2["Trigger B: create/update rule<br/>→ apply all of THIS user's due rules"] --> Q
  Q["findDueRules: is_active AND next_date ≤ today(Seoul)<br/>[AND user_id = :uid]"] --> Each
  Each["for each rule (own transaction; error isolated per rule)"] --> D["dueOccurrences(nextDate, frequency, anchorDay, today)<br/>max 62 per run; monthly/yearly clamp to month length,<br/>keep anchorDay (Jan31→Feb28→Mar31)"]
  D --> R["amount per date:<br/>KRW rule → amount_krw<br/>USD rule → round(original × rate on/before date, else current)"]
  R --> I["INSERT finance_transactions … ON CONFLICT (recurring_id,date)<br/>WHERE recurring_id IS NOT NULL DO NOTHING"]
  I --> U["UPDATE rule SET next_date=:new, last_applied=:last<br/>WHERE id=:id AND user_id=:uid AND next_date=:old"]
  U --> C{"updated 1 row?"}
  C -- yes --> Commit[commit]
  C -- "no (concurrent run advanced it)" --> RB["rollback this rule (inserts deduped anyway)"]
  Commit --> N["after commit: Telegram summary unless quiet hours<br/>(Phase 6; at-least-once)"]
```

## 4. Authentication and data ownership

```mermaid
sequenceDiagram
  participant B as Browser (Luyra)
  participant SA as security-api
  participant FA as finance-api
  participant DB as PostgreSQL
  B->>SA: POST /api/auth/login (or email-code / Google ID token) [+ MFA]
  SA-->>B: access JWT + rotating refresh token
  B->>FA: GET /api/finance/transactions (Bearer)
  FA->>FA: JwtAuthenticationFilter: signature, expiry, revoked jti,<br/>user exists, email verified → UserPrincipal(id)
  FA->>DB: SELECT … WHERE user_id = principal.id AND …
  Note over FA,DB: user_id never from body/path/query.<br/>Referenced ids checked with findByIdAndUserId.<br/>Other user's id → 404. Composite FKs back it in the DB.
  DB-->>FA: rows
  FA-->>B: 200 ApiResponse
  B->>FA: (access token expired) → 401
  B->>SA: POST /api/auth/refresh (rotates once)
  B->>FA: retry once with new token
```

## 5. Migration and rollback

```mermaid
flowchart TD
  P0["Phase 0: metadata-only checks<br/>(schema, RLS, dup counts, owner ids)"] --> M["Identity mapping table<br/>same_uuid / explicit / excluded — owner approves"]
  M --> Reh["Rehearsal: Neon branch → local H-Space copy<br/>export → stage → pre-checks → import → reconcile"]
  Reh -->|mismatch| Fix["Stop; report counts; owner decides fix in source"] --> Reh
  Reh -->|clean + timed| Freeze["Freeze Luyra finance writes;<br/>remove Vercel finance crons"]
  Freeze --> Copy["Production export → import (one transaction)"]
  Copy --> Rec{"Reconcile counts, KRW/USD sums, md5(ids)<br/>+ API totals match?"}
  Rec -- no --> Abort["Truncate target finance tables;<br/>unfreeze Luyra; restore crons"]
  Rec -- yes --> Flip["NEXT_PUBLIC_DATA_SOURCE_FINANCE=spring<br/>Luyra signs in via H-Space"]
  Flip --> Smoke{"Smoke test OK?"}
  Smoke -- no --> RB["Flag OFF; copy back rows changed since T0<br/>(same ids, reverse mapping); reconcile; unfreeze"]
  Smoke -- yes --> Jobs["Enable finance jobs one by one"]
  Jobs --> Keep["Neon kept read-only 30 days, then archived"]
```

## 6. Budget upsert (missing-unique fix)

```mermaid
sequenceDiagram
  participant C1 as Request A
  participant C2 as Request B (same user+category)
  participant DB as PostgreSQL
  Note over C1,DB: Existing: SELECT then UPDATE or INSERT (3 autocommit stmts) → both may INSERT → duplicate budgets
  C1->>DB: INSERT … ON CONFLICT (user_id, category_id) DO UPDATE SET amount_krw, updated_at
  C2->>DB: INSERT … ON CONFLICT (user_id, category_id) DO UPDATE …
  DB-->>C1: 1 row
  DB-->>C2: 1 row (last writer wins, still exactly one row)
```
