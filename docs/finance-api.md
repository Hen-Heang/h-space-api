# finance-api

Personal finance module for H-Space. It is where the Luyra finance
backend is moving. It runs on port 8083 and is secured by
`security-api`'s JWTs, the same way as `hub-api`. Swagger group:
`finance` (`/api/finance/**`).

## Current state

**Phase 1a is built on a branch and not merged yet.** It contains
categories and payment methods, from `docs/finance/API-CONTRACT.md`
#1–8:

| Path | Methods |
|---|---|
| `/api/finance/categories` | `GET`, `POST` |
| `/api/finance/categories/{id}` | `PATCH`, `DELETE` |
| `/api/finance/payment-methods` | `GET`, `POST` |
| `/api/finance/payment-methods/{id}` | `PATCH`, `DELETE` |

How it works:

- **Migrations.** Its own Flyway history: `db/finance-migration`, table
  `flyway_finance_schema_history`, set up in `FinanceFlywayConfig`. In
  prod, `finance.flyway.run-security-migrations` is false, so
  `security-api` must migrate first. Migration so far:
  `V1__finance_lookups.sql`.
- **Ownership.** Every repository finder takes the caller's user id from
  the JWT. Another user's id answers 404.
- **Duplicate names.** A name the user already has (ignoring case)
  answers 409. The service pre-checks it, and the unique index
  `(user_id, lower(name))` backs it up. The index violation is recognized
  by SQLState too, because Hibernate can't parse constraint names from a
  non-English Postgres server.
- **Deletes.** They don't check usage yet, because nothing references
  these tables until V2 (transactions).
- **No money yet.** This slice has no monetary fields. Amounts arrive
  with transactions (Phase 1b) as `BigDecimal` / `NUMERIC`.

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
