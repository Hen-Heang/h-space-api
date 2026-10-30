# Module Architecture

Dependency direction is one-way — never introduce a cycle:

```
api-core  (shared library, no bootJar, not runnable)
    ^
security-api  (auth service, port 8080, runnable)
    ^
open-api      (sample business API, port 8082, runnable, depends on security-api)
hspace-api    (H-Space product API, port 8084, runnable, depends on security-api)
```

`open-api` and `hspace-api` are siblings — neither depends on the other.

- `api-core` — `ApiResponse` / `ApiStatus` / `StatusCode` envelope types, pagination, enum converters, interceptors. Everything else depends on this.
- `security-api` — the actual auth service: signup/login, JWT issue+refresh, Google ID-token login, MFA, password reset, user management, audit logging, rate limiting, Swagger UI.
- `open-api` — sample business API secured by `security-api`'s JWT.
- `hspace-api` — the H-Space product API: the data areas moving off Supabase, one package-per-area set (`controller/ service/ repository/ domain/ payload/`) inside one module. It embeds `security-api`'s filter chain, so it accepts the same JWTs against the same database. Currently holds the Inbox (`/api/v1/inbox-items`). Plan, order and per-area checklist: `docs/spring-migration-plan.md`.
- `legacy/spring-jwt-auth/` — archived standalone project, **not** in `settings.gradle`, not part of the build. Reference only, don't extend it.

Shared dependency versions/config live in the root `build.gradle` (`subprojects { }`), not per-module.
