# Module Architecture

Dependency direction is one-way — never introduce a cycle:

```
api-core  (shared library, no bootJar, not runnable)
    ^
security-api  (auth; runs alone only for its tests and bootRun, port 8080)
    ^
hub-api       (THE deployed H-Space app: embeds security-api; hub + finance areas;
               port 8084 with bootRun, 8080 in its image)

api-core
    ^
engineering-api (owner-only Engineering Hub, port 8085, runnable, depends on api-core ONLY)
```

Two apps deploy: `hub-api` and `engineering-api`. Until 2026-11-26 there were
five runnable modules — `security-api`, `hub-api`, `finance-api` and a sample
`open-api` — and each product app embedded a full copy of auth anyway
(component scan), with its own drifting config. `finance-api` moved into
`hub-api` (package `com.henheang.financeapi` unchanged) and `open-api` was
removed (it's in git history).

`engineering-api` stands apart: it verifies Supabase access tokens itself
(against the project's published keys, ES256/RS256 only), so it has no
`security-api` code, tables or scheduled jobs. See `docs/engineering-api.md`.

- `api-core` — `ApiResponse` / `ApiStatus` / `StatusCode` envelope types, pagination, enum converters, interceptors. Everything else depends on this.
  It also holds `RequestIdFilter` (registered by `RequestIdConfig`, first in the servlet chain): every request gets an `X-Request-Id` — the caller's, if it's a plain token of ≤64 `[A-Za-z0-9._-]` chars, else a new UUID — which is echoed in the response (CORS-exposed), put in every error body as `status.requestId` (`GlobalExceptionHandler`, and `RateLimitingFilter`'s 429; omitted on success), and printed on each log line via MDC `requestId` (`logging.pattern.correlation` in each module's `application.yml`). It's a correlation id only: never use it for auth or idempotency, or put it in SQL. security-api `@Import`s the config because it doesn't scan `com.henheang.apicore`.
- `security-api` — the actual auth service: signup/login, JWT issue+refresh, Google ID-token login, MFA, password reset, user management, audit logging, rate limiting, Swagger UI.
- `hub-api` — the deployed app: auth (all of `security-api`, embedded by component scan) plus the product areas, one package set per area — plan of record: `docs/spring-migration-plan.md`. Areas: finance (`com.henheang.financeapi`, `/api/finance/**`, H-Space money / Luyra — `docs/finance-api.md`, own migrations `db/finance-migration`, history `flyway_finance_schema_history`), the Quick Capture Inbox (`/api/hub/inbox-items`, cutover pending) and H-Vault (`/api/hub/vault`), which stores browser-encrypted ciphertext only and requires MFA — read `docs/vault-design.md` before changing it. Its tables have their own Flyway migrations and history (`db/hub-migration`, `flyway_hub_schema_history`).

Configuration: every auth and shared setting lives once in `security-api/src/main/resources/security-api.yml` and `security-api-{profile}.yml`; both apps' `application.yml` import it (`spring.config.import`). Precedence, highest first: `security-api-{profile}.yml` > the app's `application-{profile}.yml` > `security-api.yml` > the app's `application.yml` — so keep only app-specific keys (name, port, `hub.*`) in `hub-api`.

## Scheduled jobs

hub-api component-scans `com.henheang.securityapi`, so security-api's `@Scheduled` jobs fire in hub-api too, and in every container of it (and in a locally running security-api against the same database). A job therefore must:

- run in a transaction and call `ScheduledJobLock.tryAcquire(jobName)` (api-core, `com.henheang.apicore.config`) first, returning if it gets `false`. That's a PostgreSQL `pg_try_advisory_xact_lock`, released automatically at commit or rollback;
- be idempotent, because the lock stops concurrent runs, not a second run moments later;
- take its cron from config (`-` disables it) with `zone = "${app.scheduling.zone:UTC}"`, so the run time doesn't depend on the host's timezone;
- delete or update in bulk queries, never load-all-then-filter.

Current jobs: `ScheduledTasks.cleanupExpiredTokens` (security-api, `app.scheduling.token-cleanup-cron`, daily). It deletes expired password-reset, account-unlock, email-verification, refresh and revoked-token rows. Also `VaultCleanupTask` (hub-api, `hub.vault.cleanup-cron`, hourly).

Shared dependency versions/config live in the root `build.gradle` (`subprojects { }`), not per-module.
