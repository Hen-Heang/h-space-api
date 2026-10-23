# Module Architecture

Dependency direction is one-way — never introduce a cycle:

```
common-api  (shared library, no bootJar, not runnable)
    ^
security-api  (auth service, port 8080, runnable)
    ^
open-api      (sample business API, port 8082, runnable, depends on security-api)
```

- `common-api` — `ApiResponse` / `ApiStatus` / `StatusCode` envelope types, pagination, enum converters, interceptors. Everything else depends on this.
- `security-api` — the actual auth service: signup/login, JWT issue+refresh, Google ID-token login, MFA, password reset, user management, audit logging, rate limiting, Swagger UI.
- `open-api` — sample business API secured by `security-api`'s JWT.
- `legacy/spring-jwt-auth/` — archived standalone project, **not** in `settings.gradle`, not part of the build. Reference only, don't extend it.

Shared dependency versions/config live in the root `build.gradle` (`subprojects { }`), not per-module.
