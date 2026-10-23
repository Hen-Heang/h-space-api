# Module Breakdown

Full architecture rule: `.claude/rules/architecture.md` / `docs/architecture.md`.

## `common-api` — `com.henheang.commonapi`

Base library, no bootJar, not runnable. Packages:
`components/`, `components/common/`, `components/common/api/`.

- `ApiResponse<T>` / `ApiStatus` / `StatusCode` — the response envelope (see
  `.claude/context/api-style.md` for the actual builder pattern and its
  quirks).
- `ExitCode` — a second, parallel status-code enum actually used for
  `security-api`'s domain/auth exceptions (see api-style.md — `StatusCode`
  and `ExitCode` currently coexist, don't assume they're interchangeable).
- `Pagination` — wraps a Spring Data `Page<?>`; defined but not yet wired
  into any `security-api` controller response as of this writing.
- `GenericEnum<T,E>` / `AbstractEnumConverter<T,E>` — JSON-serialization-
  consistent enum pattern for JPA `@Converter`s.
- `Common` — envelope metadata (`api_id`/`request_id`/`device_id`) pulled
  from request headers.
- `CustomInterceptor` — currently an empty `@Component` stub, not wired to
  any `HandlerInterceptor` logic yet.

## `security-api` — `com.henheang.securityapi` (port 8080)

Packages: `config/ controller/ domain/ exception/ payload/(+mfa/) repository/
security/(+crypto/, +oauth/) service/(+impl/) utils/ validation/`.

- `controller/` — `AuthController`, `UserController`, `AuditController`,
  `MfaController`, `PublicController`, all extending `BaseController` (which
  supplies the `ok()`/`buildResponse()` envelope helpers).
- `security/oauth/GoogleTokenVerifier` — ID-token verification only, not a
  cookie-based OAuth2 redirect flow (that pattern was removed — see
  `.claude/rules/security.md`).
- `exception/GlobalExceptionHandler` — central exception → `ApiResponse`
  translation; see api-style.md for how it actually maps statuses.
- Schema is Flyway-managed from this module
  (`src/main/resources/db/migration/`) — see `.claude/context/database.md`.
- Depends on `common-api` only.

## `open-api` — `com.henheang.openapi` (port 8082)

Packages: `config/ controller/ domain/ enums/ payload/ repository/ service/
util/`.

- Sample business API (to-do lists/items) secured by JWTs issued by
  `security-api`. Depends on both `common-api` and `security-api`.

## `legacy/spring-jwt-auth/`

Archived standalone project. Not in `settings.gradle`, not part of the
build, not part of dependency resolution. Reference-only — never extend it
or pull code from it into an active module.
