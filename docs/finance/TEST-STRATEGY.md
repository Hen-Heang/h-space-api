# Test strategy

These rules come from `AGENTS.md` and `.claude/rules/testing.md`, applied
to finance.

## Layers

| Layer | Scope | Tooling | Database |
|---|---|---|---|
| Pure domain | `KrwConverter`, `RecurringSchedule`, budget plan, financial health, spending class, merchant normalization, subscription detection | JUnit 5, parameterized golden cases | none |
| Integration (default) | Controller → service → repository → Flyway schema | `@SpringBootTest` + MockMvc, real Postgres `jwt_auth_test`, schema `finance_api_test` | real (no persistence mocks) |
| Concurrency | Budget upsert, contributions, template cap, recurring apply, `@Version` conflicts | Two threads/executors sharing a `CountDownLatch`, separate transactions | real |
| Migration | Flyway applies on an empty schema; history table name; `ddl-auto=validate` passes; constraint behaviour (unique, composite FK, CHECK) | Integration test + direct `JdbcTemplate` inserts expecting violations | real |
| Contract | Response field sets and JSON types match `luyra:types/finance.ts` | JSON snapshots from synthetic data; Luyra-side Vitest uses the same snapshots | real |
| Parity (Phase 4–5) | Spring vs Luyra outputs for one synthetic dataset | Local script runs both against identical seeded databases and diffs totals | local only |
| Reconciliation (Phase 7) | Source vs target counts, sums, id hashes | SQL in AUTH-AND-DATA-MIGRATION §6 | rehearsal copies, then production read-only |

## Fixtures

- Two users per test class, created through the repository like `hub-api`
  tests do. Both have email verified. JWTs come from `JwtTokenProvider`.
- Only synthetic names and amounts (for example "Test Food", ₩12,345,
  $12.34). Never copy real descriptions, merchants or amounts from Luyra.
- A fixed `Clock` (Asia/Seoul) bean in tests, used for boundary cases
  such as 2027-01-31 23:59 KST, 2027-02-01 00:30 KST, leap years and the
  year rollover.
- The scheduler is off in tests (`finance.jobs.enabled=false`). Jobs are
  tested by calling the job bean directly.

## Required cases per endpoint

Per `AGENTS.md`: success, malformed input, access denial/other-user ids,
duplicates, and migration behaviour.

1. Success, with the exact response shape.
2. 400 for validation (field map) and malformed JSON. A non-UUID path id
   gives 400 once gap G5 is fixed.
3. 401 without a token, and with an expired or revoked token (one shared
   test per controller).
4. **404 when another user's resource id is in the path**, and 404 when
   another user's id is in the body (category, payment method, goal,
   rule).
5. 409 for duplicates and conflicts where the endpoint has them.
6. Rows are never visible across users in any list or aggregate.

## Money assertions

- Compare `BigDecimal` values with `compareTo`, or after `setScale`, never
  with `equals` across scales.
- Golden rounding table for `KrwConverter`. It includes an exact `.5`
  product, a just-below and just-above pair, and a value large enough to
  overflow a double's integer precision.
- A JSON test asserts that money serializes as a number, with no
  scientific notation and no quotes.
- An architecture test (simple source scan in a test) fails if `double`
  or `float` appears in `com.henheang.financeapi`, except in percentage
  presentation if D-13 allows it.

## CI

No new CI job is needed. `./gradlew clean build` already runs every
module against the `postgres:16` service. A `finance-api` image job is
added in Phase 7.
