# finance-api

Personal finance module for H-Space. It is where the Luyra finance
backend is moving. It runs on port 8083 and is secured by
`security-api`'s JWTs, the same way as `hub-api`. Swagger group:
`finance` (`/api/finance/**`).

## Current state

**Phases 1a and 1b are built on branches and not merged yet.** They
cover API-CONTRACT #1–8 and #10–13:

| Path | Methods |
|---|---|
| `/api/finance/categories` | `GET`, `POST` |
| `/api/finance/categories/{id}` | `PATCH`, `DELETE` |
| `/api/finance/payment-methods` | `GET`, `POST` |
| `/api/finance/payment-methods/{id}` | `PATCH`, `DELETE` |
| `/api/finance/transactions` | `GET` (filters, sort, 20 per page), `POST` |
| `/api/finance/transactions/{id}` | `PATCH` (full replace, like Luyra), `DELETE` |

How it works:

- **Migrations.** Its own Flyway history: `db/finance-migration`, table
  `flyway_finance_schema_history`, set up in `FinanceFlywayConfig`. In
  prod, `finance.flyway.run-security-migrations` is false, so
  `security-api` must migrate first. Migrations so far:
  `V1__finance_lookups.sql` and `V2__finance_transactions.sql`. V2 needs
  **PostgreSQL 15+** for `ON DELETE SET NULL (recurring_id)`.
- **Ownership.** Every repository finder takes the caller's user id from
  the JWT. Another user's id answers 404.
- **Duplicate names.** A name the user already has (ignoring case)
  answers 409. The service pre-checks it, and the unique index
  `(user_id, lower(name))` backs it up. The index violation is recognized
  by SQLState too, because Hibernate can't parse constraint names from a
  non-English Postgres server.
- **References are the owner's own.** A transaction can only point at its
  owner's category, payment method or recurring rule. The service checks
  this (404 otherwise), and composite foreign keys `(x_id, user_id)` back
  it up in the database. The category's type must fit the transaction.
- **Deletes.** A category or payment method still used by transactions
  or recurring rules answers 409 with the counts, in Luyra's wording.
- **Money** is `BigDecimal` ↔ `NUMERIC` end to end:
  - KRW is whole won (`NUMERIC(14,0)`).
  - USD keeps its original amount (2 decimals) and rate (6 decimals).
  - A USD entry's KRW is computed once, in `KrwConverter`, as
    `original × rate` with `HALF_UP`. Any KRW figure the client sends
    for a USD entry is ignored.
  - This can be ₩1 above Luyra's floating-point result at an exact half
    (D-7). `KrwConverterTest` pins that case.

## Plan of record

The migration of Luyra finance into this module is prepared in
[`docs/finance/`](finance/README.md). That folder holds the current
architecture, schema, API contract, identity and data-migration plan,
phased implementation plan, and open decisions. Phase 0 (preparation) is
written. **No implementation phase is approved yet.**

Key facts:

- **Where the data lives today.** Finance records are in **Luyra's Neon
  database** (`finance_*` tables), with no row-level security. Identity
  comes from Supabase Auth. An earlier version of this page said the
  records were in Supabase under RLS; that was wrong.
- **Ownership and API contract:**
  [`finance/API-CONTRACT.md`](finance/API-CONTRACT.md). It keeps Luyra's
  paths and field names, with an `ApiResponse` adapter on the client.
- **User mapping:**
  [`finance/AUTH-AND-DATA-MIGRATION.md`](finance/AUTH-AND-DATA-MIGRATION.md).
  Each user is verified and mapped; UUID parity is never assumed.
- **Authorization.** Every query is scoped to the caller's user id.
  Referenced ids are ownership-checked and backed by composite FKs.
  Another user's ids answer 404.
- **Money.** `NUMERIC` / `BigDecimal`. KRW is canonical and in whole won;
  USD keeps its original amount and rate.
- **Reconciliation and rollback.** See the data-migration doc §6–8.

The first slice is Phase 1a in
[`finance/IMPLEMENTATION-PLAN.md`](finance/IMPLEMENTATION-PLAN.md#recommended-first-task).
It covers module groundwork plus categories and payment methods.
