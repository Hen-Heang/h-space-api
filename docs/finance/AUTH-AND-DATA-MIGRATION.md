# Authentication and data migration plan

Status: **PROPOSED. Nothing here is executed.** Every step that reads
production finance rows needs the owner's explicit approval at the time it
runs. The phase-0 checks below read **metadata only**: catalog data, user
ids and counts.

## 1. Identity: what's known

| Fact | Label | Source |
|---|---|---|
| Luyra's user id = the `sub` from the Supabase project its docs call "Hengo". Neon `users.id` is that same UUID. | VERIFIED | `luyra:lib/auth/ensure-app-user.ts`, `luyra:docs/APP-MIGRATION.md` |
| Money Flow (the original finance app) was a *different* Supabase project. Its single account holding history was mapped to its Luyra account by hand, not by email, and copied in August 2026. | VERIFIED (docs) | `luyra:docs/APP-MIGRATION.md` |
| H-Space `users` were imported from H-Space's Supabase project with the **same UUIDs**. Only accounts with a `kori_profiles` row were imported. The import doc says that project "is shared with Orbit/Luyra". | VERIFIED (docs) | `docs/supabase-user-import.md` |
| Luyra's "Hengo" project is the same Supabase project H-Space imported from, so a person's Luyra UUID equals their H-Space UUID. | **INFERRED, unproven** | — |
| Any given Luyra finance owner exists in H-Space `users` (had an H-Space profile, or signed up since). | **BLOCKED**: check needed | — |

**Rule:** never assume parity and never match on email alone. A mapping
row counts only when all three hold:

1. The UUID exists on both sides **and**
2. The two email addresses are equal (case-insensitive) **and**
3. The owner confirms the mapping.

Anything else gets an explicit mapping row that the owner approves, or the
user is left out and reported.

## 2. Phase-0 identity check (metadata only, read-only)

Run by the owner, or with their approval. Results stay off-repo.

On Luyra Neon:

```sql
-- every user id that owns finance data, with no amounts or descriptions
select user_id, 'transactions' as t, count(*) from finance_transactions group by user_id
union all select user_id, 'categories', count(*) from finance_categories group by user_id
union all select user_id, 'savings_goals', count(*) from finance_savings_goals group by user_id
-- ...one line per user-owned finance table
order by 1, 2;
select id, lower(email) from users where id in (<the ids above>);
```

On the H-Space database:

```sql
select id, lower(email), email_verified from users where id in (<same ids>);
```

**Outcome.** Build the mapping table `source_user_id → target_user_id`
with a status for each user:

| Status | Meaning |
|---|---|
| `same_uuid` | The ids match and so do the emails |
| `explicit` | The owner approved a different target id |
| `excluded` | Test or inactive account; reported, not copied |

A finance owner with no H-Space account either signs up through the
invite flow first, or is excluded.

## 3. Frontend authentication after cutover (decision D-4)

| Option | What it means | Assessment |
|---|---|---|
| **A. Luyra signs in with H-Space** (`security-api`: password, email code, Google ID token, MFA) and calls `finance-api` with the H-Space bearer token | Luyra drops Supabase Auth for finance. Learning (paused) can keep Supabase until it moves. | **Recommended.** It uses the existing auth unchanged, so there is one identity and revocation applies everywhere. |
| B. Luyra's server exchanges its Supabase session for an H-Space token | A new token-exchange endpoint in `security-api` | Rejected for now: it changes the security architecture, and the bridge code it would build on is temporary. |
| C. `finance-api` accepts Supabase JWTs | A second auth path | Rejected: it duplicates authentication logic. |

Option A needs the following:

- Luyra's origin in `CORS_ALLOWED_ORIGINS`.
- The Luyra Google client id in `GOOGLE_CLIENT_IDS`. Luyra uses a
  different Google client id from Money Flow; confirm it against H-Space
  (**BLOCKED**, D-4).
- An H-Space account for each finance owner (see §2).

## 4. Data copy

**Source.** Luyra Neon `finance_*`. **Target.** H-Space PostgreSQL
(decision D-2 confirms which database).

The copy works like this:

- Ids, `created_at` and `updated_at` are preserved.
- `user_id` is remapped through the mapping table.
- Luyra's `users` table is **not** copied, and neither are the learning,
  task and habit tables.

Order respects the FKs:

1. Mapping table (temp, target side)
2. `finance_categories`, `finance_payment_methods`
3. `finance_recurring_transactions`
4. `finance_transactions`
5. `finance_budgets`
6. `finance_savings_goals` → `finance_savings_contributions`
7. `finance_transaction_templates`
8. `finance_preferences`
9. `finance_subscription_status`, `finance_transaction_description_aliases`
10. `finance_exchange_rates`: copy all rows (needed for historical rates
    of back-dated recurring occurrences)
11. `finance_push_budget_alerts`, Telegram/push tables: only if D-10 moves
    notifications to `finance-api`

The **mechanism** follows the Inbox runbook pattern
(`docs/spring-migration-plan.md`) and is extended per table:

1. Export: `psql \copy (select … order by id) to '<file>.csv'` against the
   **source**, read-only. Keep the files outside every repository and
   delete them afterwards.
2. Import into the target **in one transaction**:
   - Load into `create temp table <t>_stage` tables first.
   - Run the **pre-checks** below; any non-zero result aborts the import.
   - Then `insert … select … on conflict (id) do nothing`.
3. The destination schema already has the new constraints (unique budget,
   composite FKs, CHECKs). Rows that violate them fail the transaction.
   They are never cleaned silently.

**Pre-checks**, each of which must return 0 or the import aborts:

- A stage `user_id` that isn't in the mapping table
- A child that references a parent owned by a different user (category,
  payment method, recurring rule, goal)
- Duplicate `(user_id, category_id)` budgets, `(user_id, subscription_key)`
  rows, `(user_id, normalized_key)` aliases, or `(user_id, lower(name))`
  categories and payment methods
- `amount_krw <> trunc(amount_krw)`, `amount_krw <= 0` on transactions,
  null `description`, null category `type`
- A USD row missing `amount_usd` or `exchange_rate`, or a KRW row that has
  them

If any pre-check fails, **stop**. Report the counts (not the rows) to the
owner, who decides on a correction made in the source through the
existing Luyra UI, or on an approved, scripted fix.

## 5. Isolated rehearsal

1. Create a **Neon branch** of Luyra's production branch. Neon branches
   are copy-on-write, so creating one doesn't modify the parent.
2. Restore a copy of the H-Space database locally, or use a fresh local
   database with `security-api` and `finance-api` Flyway applied plus the
   mapped `users` rows.
3. Run export → import → reconcile end to end against the copies. Repeat
   until everything is clean.
4. Time the run. It sets the freeze window for production.

## 6. Reconciliation

Run identical queries on source and target, grouped by mapped user.
Every line must match:

```sql
-- ledger per user per month
select user_id, to_char(date,'YYYY-MM') m, type, count(*),
       sum(amount_krw), sum(coalesce(amount_usd,0)),
       md5(string_agg(id::text, ',' order by id))
from finance_transactions group by 1,2,3 order by 1,2,3;
-- savings
select g.id, g.current_usd, count(c.id), coalesce(sum(c.amount_usd),0)
from finance_savings_goals g left join finance_savings_contributions c on c.goal_id=g.id
group by g.id, g.current_usd order by g.id;
-- lookups, budgets, rules, templates: count + md5(ids) per user,
-- budgets also sum(amount_krw); preferences row-for-row compare.
```

Then check that the API agrees with the data:

- For three synthetic-checked months, `GET /overview` and
  `GET /reports/monthly` on Spring return the same totals as Luyra for the
  same user and month.
- Compare totals only, and keep the outputs off-repo.

## 7. Cutover (single writer, no dual writes)

```text
T-1d  Rehearsal green; Spring finance-api deployed, jobs OFF, flag OFF.
T0    Freeze: Luyra finance read-only (maintenance banner / flag in Luyra);
      remove finance crons from vercel.json and redeploy Luyra.
      (Do it after that day's 09:00 KST recurring run so no occurrence is
      pending.)
T0+   Export → import → reconcile (§4–6) against production.
T0+   Flip NEXT_PUBLIC_DATA_SOURCE_FINANCE=spring; Luyra signs in with H-Space.
T0+   Smoke test: list/create/edit/delete transaction, budget save,
      contribution, overview month, export.
T0+   Enable FINANCE_JOBS_ENABLED=true (jobs one area at a time).
```

During and after the cutover:

- At no point do both backends accept writes.
- At no point do both cron producers run.
- Neon stays untouched and read-only for a 30-day retention window, then
  it is archived (D-14).

## 8. Rollback

| When | Steps |
|---|---|
| Before the flag flip | Un-freeze Luyra and restore `vercel.json` crons. Nothing changed in the source. Truncate the target finance tables, which hold only copied data. |
| After the flip | 1. Set Spring jobs OFF and the flag OFF. <br>2. **Copy back** rows created or changed in Spring since T0: same ids, `updated_at > T0`, `on conflict (id) do update`, with the reverse user mapping. Also delete source rows whose ids were deleted in Spring (keep a deletion log table, or compare id sets). <br>3. Reconcile. <br>4. Un-freeze Luyra on Neon and restore the crons. |
| Rehearse | Rehearse the reverse copy once on the Neon branch before production. |
