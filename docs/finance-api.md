# finance-api

Personal finance module for H-Space (money tracking / Luyra integration).
Runnable on port 8083, secured by `security-api`'s JWTs the same way as
`open-api`. Swagger group: `finance` (`/api/finance/**`).

## Current state

Scaffold only: application class, profiles, Swagger group, and a test that
the app boots and `/api/finance/**` rejects unauthenticated requests. No
entities, migrations or endpoints yet.

## Before adding finance data

Finance records currently live in Supabase (shared with Luyra, under RLS).
Per the root `CLAUDE.md`, document these before the first table lands here:

- [ ] **Ownership** — which records move to this API, and which stay in
      Supabase/Luyra.
- [ ] **API contract** — endpoints and payloads agreed with the H-Space web
      frontend, with a frontend test contract.
- [ ] **User mapping** — rows keyed by the `users.id` UUID (same UUIDs as
      Supabase after the account import).
- [ ] **Authorization** — what replaces RLS: every query scoped to the
      caller's user ID; other users' IDs return 404/403 (tested).
- [ ] **Money representation** — `NUMERIC`/`BigDecimal` plus an ISO-4217
      currency code; never `double`/`float`.
- [ ] **Reconciliation and rollback** — how Supabase and this API are kept
      consistent during migration, and how to back out.

Then implement one small tested vertical slice (controller → service →
repository, Flyway migration, ownership tests) before anything wider.
