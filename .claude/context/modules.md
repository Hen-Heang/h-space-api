# Module Breakdown

Full architecture rule: `.claude/rules/architecture.md` / `docs/architecture.md`.

## `api-core` — `com.henheang.apicore`

Base library, no bootJar, not runnable. Packages:
`components/`, `components/common/`, `components/common/api/`.

- `ApiResponse<T>` / `ApiStatus` / `StatusCode` — the response envelope (see
  `.claude/context/api-style.md` for the actual builder pattern and its
  quirks).
- `StatusCode` also holds the auth codes (1000s–1500s, 5000s, 9999) that
  `security-api`'s `AuthException` carries — the former `ExitCode` enum was
  merged into it (see api-style.md).
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
- Depends on `api-core` only.

## `hub-api` — the deployed app (port 8084 with bootRun, 8080 in its image)

Embeds all of `security-api` (component scan) and holds the product areas,
one package set per area:

- `com.henheang.hubapi` — Quick Capture Inbox (`/api/hub/inbox-items`) and
  H-Vault (`/api/hub/vault`; `.claude/rules/vault.md`). Migrations
  `db/hub-migration`, history `flyway_hub_schema_history`.
- `com.henheang.financeapi` — finance (`/api/finance/**`; `docs/finance-api.md`),
  a separate `finance-api` module until 2026-11-26. Migrations
  `db/finance-migration`, history `flyway_finance_schema_history`.

`HubFlywayConfig` migrates security, then hub, then finance at start-up. Its
config imports `security-api.yml`; keep only hub-specific keys in
`hub-api`'s own yml. The sample `open-api` module was removed 2026-11-26.
