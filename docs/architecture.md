# Module Architecture

Dependency direction is one-way — never introduce a cycle:

```
api-core  (shared library, no bootJar, not runnable)
    ^
security-api  (auth service, port 8080, runnable)
    ^
open-api      (sample business API, port 8082, runnable, depends on security-api)
finance-api   (personal finance API, port 8083, runnable, depends on security-api)
hub-api       (personal-hub data API, port 8084, runnable, depends on security-api)
```

`open-api`, `finance-api` and `hub-api` are siblings — none depends on another.

- `api-core` — `ApiResponse` / `ApiStatus` / `StatusCode` envelope types, pagination, enum converters, interceptors. Everything else depends on this.
  It also holds `RequestIdFilter` (registered by `RequestIdConfig`, first in the servlet chain): every request gets an `X-Request-Id` — the caller's, if it's a plain token of ≤64 `[A-Za-z0-9._-]` chars, else a new UUID — which is echoed in the response (CORS-exposed) and printed on each log line via MDC `requestId` (`logging.pattern.correlation` in each module's `application.yml`). It's a correlation id only: never use it for auth or idempotency, or put it in SQL. security-api `@Import`s the config because it doesn't scan `com.henheang.apicore`.
- `security-api` — the actual auth service: signup/login, JWT issue+refresh, Google ID-token login, MFA, password reset, user management, audit logging, rate limiting, Swagger UI.
- `open-api` — sample business API secured by `security-api`'s JWT.
- `hub-api` — personal-hub data moving off Supabase (`/api/hub/**`), one package set per area — plan of record: `docs/spring-migration-plan.md`. Areas: the Quick Capture Inbox (`/api/hub/inbox-items`, cutover pending) and H-Vault (`/api/hub/vault`), which stores browser-encrypted ciphertext only and requires MFA — read `docs/vault-design.md` before changing it. Its tables have their own Flyway migrations and history (`db/hub-migration`, `flyway_hub_schema_history`).
- `finance-api` — personal finance (H-Space money / Luyra) API secured by `security-api`'s JWT. Currently a scaffold with no domain tables — read `docs/finance-api.md` before adding any.

Shared dependency versions/config live in the root `build.gradle` (`subprojects { }`), not per-module.
