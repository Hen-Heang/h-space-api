# engineering-api — the Engineering Hub

An owner-only Spring service for running H-Space's infrastructure: a project
registry, a **Neon usage guard**, and CI status. It exists for daily use and
for practice (external APIs, JWKS-based security, scheduled jobs, Flyway,
Docker, Railway). H-Space's web app never depends on it.

**Why the Neon guard first:** in November 2026 one idle connection kept the
`h-space-api` Neon compute awake from 11-09 to 11-19 and used up the free
quota, taking Finance and H-Vault down (h-space `docs/SPRING_EXIT_PLAN.md`).
A daily projection against a budget would have flagged it on day 1–2.

## Status

| Slice | What | State |
|---|---|---|
| S0 | `ScheduledJobLock` and `UserScopedRepositoryRules` moved to `api-core` | done (#51) |
| S1 | Module, security, Flyway schema, CI image | done (#52) |
| **S2** | **Project registry (CRUD)** | **this change** |
| S3 | Neon usage client + snapshots + `GET /overview` | |
| S4 | Daily guard job + email alerts | |
| S5 | GitHub CI status | |
| S6 | Railway deploy, runbook, restore drill | |

## Decisions (owner, 2026-11-25)

1. Require `aal2` (a session that entered an authenticator code).
2. Only engineering-api runs on Railway; security-api stays built and tested
   in CI but undeployed.
3. Alerts by email (Resend SMTP).
4. Shared pieces (`ScheduledJobLock`, the repository-scoping rules) live in
   `api-core` (S0), so this module needs nothing from `security-api`.
5. Budget for the `h-space-api` Neon project: 20 CU-hours a month.
6. This document is the plan of record.
7. **People use their Supabase session; the job uses a key.** The daily
   GitHub Actions job (S4) can't hold a browser session, so it gets its own
   secret, `ENGINEERING_JOB_SECRET`, stored only in Railway and GitHub
   Actions secrets. It's checked in constant time by a separate security
   chain and opens **only** `POST /api/engineering/jobs/**`; a session token
   doesn't open the job endpoint, and the key opens nothing else. Without
   the variable the job endpoint refuses everything.

## Architecture

- **Module:** `engineering-api`, package `com.henheang.engineeringapi`, port
  8085. Depends on `api-core` only (`docs/architecture.md`): the
  `ApiResponse` envelope and `RequestIdConfig`. It scans only its own
  package.
- **Database:** its own Neon project in production (max 0.25 CU,
  scale-to-zero, pools that drain to zero). Schema `engineering`, Flyway
  migrations in `db/engineering-migration`, history table
  `flyway_engineering_schema_history`. Tests use schema
  `engineering_api_test`. Infrastructure metadata only — no personal data.
- **External calls (S3, S5):** Neon's control-plane API (it never wakes a
  compute) and GitHub's REST API, read-only, with timeouts and caching.
- **Scheduling (S4):** a daily GitHub Actions cron calls a secret-protected
  job endpoint, so the app can sleep on Railway; a failing call fails the
  workflow, which emails the owner.

## Security model (S1)

Every request to `/api/engineering/**` carries a Supabase access token from
the owner's H-Space session.

| Check | Where | Failure |
|---|---|---|
| Signature with a key from `<issuer>/.well-known/jwks.json`, **ES256 or RS256 only** | `SupabaseJwtDecoderConfig` | 401 |
| `iss` equals `SUPABASE_AUTH_ISSUER`; `aud` contains `authenticated`; not expired | `SupabaseJwtDecoderConfig` | 401 |
| `sub` in `ENGINEERING_OWNER_IDS` **and** `aal` = `aal2` | `OwnerAuthenticationConverter` | 403 |

- **No secret that can mint tokens.** Only public keys are read, so the
  service can't forge a session — unlike security-api's retired Supabase
  bridge. An HS256 token (e.g. signed with Supabase's legacy shared secret)
  is refused before any key is tried, even while that secret still exists.
- **Fail closed at startup:** no issuer, or no owner id, and the app doesn't
  start (`EngineeringAuthProperties`).
- 401 and 403 come back in the `ApiResponse` envelope with the request id
  (`ApiErrorWriter`); the reason a token was rejected is never echoed.
- Only `/actuator/health` (and its liveness/readiness groups) is public.
  Swagger UI exists only when `API_DOCS_ENABLED=true` (on in `dev`).
- Every repository method is scoped by the owner id (the token's `sub`,
  stored as `user_id`), enforced at build time by
  `UserScopedRepositoryArchTest` (`UserScopedRepositoryRules` from
  `api-core`'s test fixtures). The owner id never comes from a body, path or
  query (`CurrentOwner`); request bodies are allowlists — unknown JSON fields
  are a 400.

## Endpoints

All answer in the `ApiResponse` envelope; all need the owner (see above).

| Method + path | Body | Returns |
|---|---|---|
| `GET /api/engineering/session` | — | `ownerId`, `assuranceLevel`, token expiry: an end-to-end check of the chain |
| `GET /api/engineering/projects` | — | the caller's projects, sorted by name |
| `POST /api/engineering/projects` | project fields, **no** `version` | 201 + the project; 409 for a slug the caller already uses |
| `GET /api/engineering/projects/{id}` | — | the project; **404 also for another owner's id** |
| `PUT /api/engineering/projects/{id}` | all fields + the `version` it was read at | the project with `version + 1`; 409 if it changed elsewhere or the slug is taken; 400 without `version` |
| `DELETE /api/engineering/projects/{id}` | — | 200 |

**Project fields** (`ProjectRequest`, the same rules as the table's CHECK
constraints in `V2__projects.sql`): `name` (≤ 80), `slug` (lowercase,
digits, single hyphens; unique per owner), `description` (≤ 500),
`githubRepo` (`owner/name`), `productionUrl` (`https://`, dotted host, no
credentials), `hosting` (`vercel|railway|neon|supabase|github|other`),
`neonProjectId`, `monthlyCostCents` + `costCurrency` (both or neither),
`computeBudgetCuHours` (for the S4 guard). At most 100 projects per owner.

## Configuration

| Variable | Needed | Notes |
|---|---|---|
| `SUPABASE_AUTH_ISSUER` | yes | `https://<project-ref>.supabase.co/auth/v1` (the web app's `SUPABASE_OAUTH_ISSUER`) |
| `ENGINEERING_OWNER_IDS` | yes | Comma-separated Supabase user ids |
| `SUPABASE_AUTH_AUDIENCE` | no | Default `authenticated` |
| `SPRING_PROFILES_ACTIVE` | prod | `prod` on Railway; default `dev` locally |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | prod | Its own database; direct (non-pooler) endpoint |
| `ENGINEERING_DB_SCHEMA` | no | Default `engineering` |
| `DB_POOL_MIN_IDLE` | no | Leave unset (0) |
| `API_DOCS_ENABLED` | no | Swagger UI in prod; off by default |

**Calling it locally:** run with `SUPABASE_AUTH_ISSUER` and
`ENGINEERING_OWNER_IDS` set, then send your H-Space access token:
`curl -H "Authorization: Bearer <token>" localhost:8085/api/engineering/session`.
The token is in the browser's `localStorage` under `koriai-auth`
(`access_token`); it expires within an hour. Never paste it anywhere shared.

## MVP scope (S2–S5)

| Feature | Detail |
|---|---|
| Project registry | name, slug, GitHub `owner/repo`, prod URL, hosting, Neon project id, manual monthly cost, CU-hour budget |
| Neon usage | `GET /api/v2/projects/{id}` (compute time, active time, period, last active) and the operations list; snapshot per run |
| Neon guard | projected usage vs budget (alerts at 50% and 80%); "never sleeps" (active ≥ 20 h of the last 24); one alert per kind per period |
| CI status | latest Actions run on the default branch, 5-minute cache with ETags |
| Overview | `GET /api/engineering/overview` |

Out of scope for now: Vercel/Railway deploy status, automatic cost import,
architecture-doc hosting, a web UI, any write to an external service.

## Risks

- **Neon API key power:** use a key limited to the monitored projects (to
  verify before S3); only GET calls, asserted by a test; never logged.
- **Usage units:** `compute_time_seconds / 3600` is assumed to be CU-hours;
  calibrate against Neon's billing page in S3.
- **Cold starts:** a sleeping JVM takes ~10–20 s to wake on Railway; fine
  for a daily cron and manual use.
- **Scope creep:** nothing outside the MVP table until it's been used daily
  for a few weeks.
