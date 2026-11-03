# Finance (Luyra → `finance-api`) — preparation docs

Status: **Phase 0 (analysis and preparation). No finance code, tables or
migrations exist yet.** Written 2026-11-03. Nothing here has been approved
for implementation. Phase 1 starts only after the owner approves it
explicitly.

This folder covers moving the **Luyra personal-finance backend**
(Next.js Route Handlers on Neon, sign-in through Supabase Auth) into the
`finance-api` module of this repo. It adds to the root documentation and
does not replace it. The rules in `AGENTS.md`, `docs/architecture.md`,
`docs/database.md` and `docs/security-api-state.md` still apply, and they
win if anything here disagrees.

## Labels

Every finding is tagged:

| Label | Meaning |
|---|---|
| **VERIFIED** | Read in the source this session. Luyra = `luyra-web` at `f63fcb8` (= `origin/main` on 2026-11-03); H-Space = this repo at `b4ded30`. |
| **INFERRED** | Follows from code or docs but was not checked directly. This includes **live-database facts reported by an earlier read-only review** that were not re-checked here (no Neon tooling was available in this session). |
| **PROPOSED** | A design choice for the port. Not built yet. |
| **BLOCKED** | Needs a decision or a check the owner must make before the dependent work starts. |

## Files

| File | What it answers |
|---|---|
| [CURRENT-ARCHITECTURE.md](CURRENT-ARCHITECTURE.md) | How Luyra finance works today, and the state of the H-Space `finance-api` scaffold |
| [DATABASE-SCHEMA.md](DATABASE-SCHEMA.md) | Every `finance_*` table: columns, keys, constraints, gaps |
| [ERD.md](ERD.md) | Entity-relationship diagrams, current and proposed |
| [SPRING-ARCHITECTURE.md](SPRING-ARCHITECTURE.md) | The proposed module layout, Flyway setup, layering and correctness rules |
| [API-CONTRACT.md](API-CONTRACT.md) | Every Luyra endpoint and its mapping to Spring |
| [AUTH-AND-DATA-MIGRATION.md](AUTH-AND-DATA-MIGRATION.md) | Identity mapping, data copy, reconciliation, cutover and rollback |
| [WORKFLOWS.md](WORKFLOWS.md) | Sequence and flow diagrams for the key business flows |
| [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | Phases 0–7, each with tasks, tests, acceptance criteria and Definition of Done |
| [TEST-STRATEGY.md](TEST-STRATEGY.md) | Test layers, fixtures and the parity/golden cases |
| [RISKS-AND-DECISIONS.md](RISKS-AND-DECISIONS.md) | Risk register and the open decisions that block development |

## Five things to know first

1. **Target module: `finance-api` (decided 2026-11-03, D-1).** This
   overrides the old note in `docs/spring-migration-plan.md` that finance
   would become a package in `hub-api`. That plan's D9 still keeps
   Luyra's *identity* out of the H-Space cutover. Finance data is handled
   here instead.
2. **The finance data is in Neon, not Supabase.** `docs/finance-api.md`
   used to say it was in Supabase. Luyra's own docs and code show the
   finance data is native in Luyra's Neon database. Only *identity* comes
   from Supabase Auth. — **VERIFIED** (code and docs)
3. **Tenant isolation lives only in application code.** The earlier
   live-database review reported that Row-Level Security is off on every
   `finance_*` table (**INFERRED**). In code, the category and
   payment-method ids that a request references are not checked for
   ownership on most write paths. — **VERIFIED** (code)
4. **The database doesn't enforce several rules the code relies on.**
   There is no unique `(user_id, category_id)` on budgets, no unique
   subscription key or alias key, and no case-insensitive unique name.
   The upserts check first and then write, outside any transaction.
   — **VERIFIED** (migrations and code)
5. **Whether user UUIDs match is unproven.** Luyra uses the `sub` from
   the Hengo Supabase project. H-Space imported `users` from a Supabase
   project that its own docs describe as "shared with Orbit/Luyra", but
   only accounts with an H-Space profile. Matching ids are likely but
   must be proven per user. — **BLOCKED** (decision D-3)

The recommended first implementation task is at the end of
[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md#recommended-first-task).

## Keep out of this repository

This repository is public. As with the H-Space live-schema snapshot
(`docs/spring-migration-plan.md`), keep the following off-repo:

- Neon project and branch identifiers
- live `pg_dump --schema-only` output
- row counts per user
- every export file

This folder only describes what the Luyra migration files already show.
