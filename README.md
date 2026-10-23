# AuthHub

[![CI](https://github.com/Hen-Heang/authhub/actions/workflows/ci.yml/badge.svg)](https://github.com/Hen-Heang/authhub/actions/workflows/ci.yml)

A multi-module **Spring Boot 3.5** monorepo for authentication and a sample
business API. It provides JWT-based authentication (with refresh + revocation),
Google ID-token login, MFA (TOTP with one-time backup codes), email
verification, password reset, account lockout with self-service unlock, RBAC
(roles/permissions), audit logging, and rate limiting — exposed as reusable
modules that other services build on top of.

- **Group:** `com.henheang`
- **Java:** 17 (set in the root `build.gradle`)
- **Build tool:** Gradle (wrapper included — `./gradlew`)
- **Spring Boot:** 3.5.15
- **Database:** PostgreSQL

---

## Module Architecture

AuthHub is a Gradle multi-project build with three modules. Dependencies flow in
one direction only (no cycles):

```
common-api   (base library — shared utilities, no app of its own)
    ▲
    │
security-api (authentication service — JWT, Google login, MFA, audit)  ── runnable
    ▲
    │
open-api     (sample business API protected by security-api)           ── runnable
```

| Module         | Port  | bootJar | Purpose |
|----------------|-------|---------|---------|
| `common-api`   | —     | ❌ off  | Shared library: API response envelopes (`ApiResponse`, `ApiStatus`, `StatusCode`), pagination, enum converters, interceptors. Built as a plain `jar` and consumed by the other modules — not run directly. |
| `security-api` | 8080  | ✅ on   | Core authentication service: signup/login, JWT issue/refresh/revocation, Google ID-token login, MFA (TOTP + backup codes), email verification, password reset, account lockout/unlock, RBAC (roles/permissions), user management, audit logging, rate limiting, Swagger UI. |
| `open-api`      | 8082  | ✅ on   | Example business API (to-do lists/items) that depends on `security-api` for authentication. |

Dependency wiring lives in the **root `build.gradle`** (`subprojects { ... }`
plus per-project blocks), so most dependencies are declared once at the top.

---

## Prerequisites

1. **JDK 17** (the build targets Java 17).
2. **PostgreSQL** running locally on port `5432` with a database named `jwt_auth`.
   - Default credentials in the configs: user `postgres`, password `123`.
   - Schema is managed by **Flyway** (`security-api/src/main/resources/db/migration/`) —
     migrations run automatically on startup. `spring.jpa.hibernate.ddl-auto` is
     `validate` in every profile; Hibernate checks the schema matches but never
     creates/alters it.
3. *(Optional)* Gmail SMTP credentials for email-based features (password reset,
   email verification, account-unlock emails).

### Create the database

```bash
createdb -U postgres jwt_auth
# or inside psql:  CREATE DATABASE jwt_auth;
```

### Configuration profiles

Each runnable module (`common-api`'s context, `security-api`, `open-api`) loads
`application.yml` plus a profile file selected by `SPRING_PROFILES_ACTIVE`
(Spring's standard `application-{profile}.yml` convention):

| Profile | File | Used by | Secrets |
|---------|------|---------|---------|
| `local` (default) | `application-local.yml` | `./gradlew bootRun` with no env vars | Has fallback defaults (DB password `123`, placeholder JWT secret) for zero-config local dev. **Git-ignored** — copy `.env.example` and fill in your own values instead of relying on the placeholder. |
| `dev` | `application-dev.yml` | Shared dev/staging | Has a hardcoded fallback JWT secret / MFA encryption key for convenience; still expects real `DB_PASSWORD`. |
| `test` | `application-test.yml` | `./gradlew test` / CI (forced via the root `build.gradle` `Test` task config) | Separate `jwt_auth_test` DB, a fixed non-sensitive JWT secret default (safe — test-only). |
| `prod` | `application-prod.yml` | Production | No fallback defaults anywhere — fails fast at startup if `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET` / `MFA_ENCRYPTION_KEY` aren't set. |

`spring.jpa.hibernate.ddl-auto` is `validate` in **every** profile — Flyway
(`spring.flyway.enabled: true`) is the sole schema authority; Hibernate only
verifies the schema on startup.

Copy `.env.example` to `.env` (or export the vars directly) and set:

```bash
export SPRING_PROFILES_ACTIVE="local"   # local | dev | test | prod
export DB_PASSWORD="..."
export JWT_SECRET="<base64 64-byte secret>"   # generate via JwtSecretGenerator#main
export MFA_ENCRYPTION_KEY="<base64 32-byte AES key>"   # generate via `openssl rand -base64 32`
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-gmail-app-password"
export GOOGLE_CLIENT_IDS=""   # comma-separated OAuth client IDs; empty disables Google sign-in
```

> ⚠️ No `application*.yml` file commits a real secret anymore. See
> [SECURITY.md](SECURITY.md) for the full policy and how to rotate the
> secret that *was* previously committed in `security-api`'s local config.

---

## Build

From the `AuthHub/` directory:

```bash
# Build everything (compiles, runs tests, produces jars)
./gradlew build

# Build a single module
./gradlew :security-api:build
./gradlew :open-api:build

# Skip tests
./gradlew build -x test
```

### Code quality tooling

Wired into every module via the root `build.gradle`:

| Tool | Task | Enforced? |
|------|------|-----------|
| **Spotless** (Google Java Format) | `spotlessCheck` / `spotlessApply` | Yes — part of `check`/`build`. Run `./gradlew spotlessApply` to auto-format. |
| **Checkstyle** | `checkstyleMain` / `checkstyleTest` | Advisory — reports only (`ignoreFailures = true`) until the existing codebase is brought into compliance. Rules in `config/checkstyle/checkstyle.xml`. |
| **PMD** | `pmdMain` / `pmdTest` | Advisory — same reasoning. Rules in `config/pmd/ruleset.xml`. |
| **JaCoCo** | `jacocoTestReport` | Runs after every `test` task; reports under `*/build/reports/jacoco/`. No minimum-coverage gate yet. |

See `docs/coding-standards.md` before tightening any of these to fail the build.

## Run

Each runnable module is started with the Spring Boot plugin:

```bash
# Start the auth service (port 8080)
./gradlew :security-api:bootRun

# Start the todo API (port 8082) — needs security-api's JWT to authenticate
./gradlew :open-api:bootRun
```

`common-api` is a library (`bootJar` disabled) and is **not** run directly — it
is pulled in as a dependency by the other two.

Once `security-api` is up, open the API docs:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health/ping:** http://localhost:8080/api/public/ping

---

## API Overview (`security-api`, port 8080)

### Authentication — `/api/auth`
| Method | Path                          | Description |
|--------|-------------------------------|-------------|
| POST   | `/api/auth/signup`            | Register a new local account |
| POST   | `/api/auth/login`              | Authenticate; returns tokens, or an MFA challenge if MFA is enabled |
| POST   | `/api/auth/oauth2/google`      | Log in/sign up with a Google ID token (server-verified, no redirect flow) |
| POST   | `/api/auth/refresh`            | Rotate a refresh token for a new access + refresh token pair (single-use) |
| POST   | `/api/auth/logout`             | Revoke the refresh token and blacklist the current access token |
| GET    | `/api/auth/user`               | Get the current authenticated user |
| POST   | `/api/auth/forgot-password`    | Start password reset (sends email token) |
| GET    | `/api/auth/reset-password?token=` | Validate a reset token |
| POST   | `/api/auth/reset-password`     | Set a new password |
| POST   | `/api/auth/verify-email`       | Confirm email via emailed verification token |
| POST   | `/api/auth/resend-verification` | Resend the email-verification email |
| POST   | `/api/auth/unlock-account`     | Unlock a locked account via emailed unlock token |
| POST   | `/api/auth/resend-unlock-link` | Resend the account-unlock email |

### MFA — `/api/auth/mfa`
| Method | Path             | Description |
|--------|------------------|-------------|
| POST   | `/api/auth/mfa/setup`   | Generate a new TOTP secret/QR for the current user (stays disabled until `/enable`) |
| POST   | `/api/auth/mfa/enable`  | Confirm a live TOTP code, enable MFA, and issue one-time backup codes |
| POST   | `/api/auth/mfa/disable` | Disable MFA (requires a valid TOTP or backup code) |
| POST   | `/api/auth/mfa/verify`  | Exchange an MFA challenge token + TOTP/backup code for real access/refresh tokens |
| POST   | `/api/auth/mfa/backup-codes/regenerate` | Invalidate all backup codes and issue a fresh set of 10 |

### Users — `/api/users`
| Method | Path               | Auth | Description |
|--------|--------------------|------|-------------|
| GET    | `/api/users`       | ADMIN | List users |
| PATCH  | `/api/users/{id}`  | ADMIN or self | Update a user |
| DELETE | `/api/users/{id}`  | ADMIN or self | Soft-delete a user |
| PATCH  | `/api/users/{id}/unlock` | ADMIN | Manually unlock a locked account |

### RBAC — `/api/admin/permissions`, `/api/admin/roles`, `/api/admin/users/{userId}/roles`
All endpoints below require the `ADMIN` role.

| Method | Path                              | Description |
|--------|------------------------------------|-------------|
| GET    | `/api/admin/permissions`          | List all permissions |
| GET    | `/api/admin/permissions/{id}`     | Get one permission |
| POST   | `/api/admin/permissions`          | Create a permission |
| DELETE | `/api/admin/permissions/{id}`     | Soft-delete a permission |
| GET    | `/api/admin/roles`                | List all roles |
| GET    | `/api/admin/roles/{id}`           | Get one role |
| POST   | `/api/admin/roles`                | Create a role |
| PATCH  | `/api/admin/roles/{id}`           | Update a role |
| DELETE | `/api/admin/roles/{id}`           | Soft-delete a role |
| POST   | `/api/admin/roles/{roleId}/permissions/{permissionId}` | Grant a permission to a role |
| DELETE | `/api/admin/roles/{roleId}/permissions/{permissionId}` | Revoke a permission from a role |
| GET    | `/api/admin/users/{userId}/roles` | List roles assigned to a user |
| POST   | `/api/admin/users/{userId}/roles/{roleId}` | Assign a role to a user |
| DELETE | `/api/admin/users/{userId}/roles/{roleId}` | Revoke a role from a user |

Permission checks (`hasPermission(...)` / `@PreAuthorize`) are backed by a
Caffeine cache (`CacheConfig`) so lookups don't hit the DB on every request;
role/permission changes evict the affected cache entries.

### Audit logs — `/api/admin/audit-logs`
All endpoints below require the `ADMIN` role.

| Method | Path                              | Description |
|--------|------------------------------------|-------------|
| GET    | `/api/admin/audit-logs`            | List audit events (paginated) |
| GET    | `/api/admin/audit-logs/user/{userId}` | List audit events for a specific user (paginated) |

Audit events cover signup, login success/failure, account lockouts, logout,
token revocation, password reset, the full MFA lifecycle, email verification,
and account unlock.

### Public — `/api/public`
| Method | Path               | Description |
|--------|--------------------|-------------|
| GET    | `/api/public/ping` | Unauthenticated health check |

Requests are also subject to `RateLimitingFilter` (Bucket4j, per-IP) on
brute-forceable endpoints (`login`, `signup`, `forgot-password`, all
`/api/auth/mfa/*`), and access/refresh tokens can be revoked via
`TokenBlacklistService` (backed by `RevokedToken`).

### Todo API (`open-api`, port 8082)
| Method | Path                   | Description |
|--------|------------------------|-------------|
| POST   | `/api/todo/v1/create`  | Create a todo list for the authenticated user |

---

## Project Layout

```
AuthHub/
├── build.gradle           # Root: plugins, shared deps, per-module wiring, quality tooling
├── settings.gradle        # Declares the 3 modules
├── gradlew / gradlew.bat  # Gradle wrapper
├── config/checkstyle/, config/pmd/  # Checkstyle & PMD rule files (see build.gradle)
├── .github/workflows/ci.yml         # CI: build + test + lint reports on push/PR
├── .env.example            # Template for local env vars — copy to .env
├── ROADMAP.md / CHANGELOG.md / SECURITY.md / CONTRIBUTING.md / LICENSE
│
├── common-api/            # Shared library (no bootJar)
│   └── src/main/java/com/henheang/commonapi/
│       └── components/     # ApiResponse, Pagination, enum converters, interceptor
│
├── security-api/          # Auth service (port 8080)
│   └── src/main/java/com/henheang/securityapi/
│       ├── config/         # WebSecurityConfig, CorsConfig, JwtConfig, OpenApiConfig,
│       │                   #   DataInitializer, ScheduledTasks, CacheConfig (permission
│       │                   #   cache), MfaEncryptionConfig (AES key for TOTP secrets)
│       ├── controller/     # AuthController, UserController, AuditController, MfaController,
│       │                   #   PermissionController, RoleController, UserRoleController,
│       │                   #   PublicController, SwaggerController
│       ├── domain/         # User, Role, Permission, UserRole, RefreshToken, RevokedToken,
│       │                   #   PasswordResetToken, EmailVerificationToken, AccountUnlockToken,
│       │                   #   MfaRecoveryCode, Device, LoginHistory, AuditLog,
│       │                   #   AuditEventType, AuthProvider, DeviceType,
│       │                   #   BaseEntity / SoftDeletableEntity / AuditBaseEntity (base classes)
│       ├── repository/     # Spring Data JPA repositories
│       ├── security/       # JWT filter/provider, RateLimitingFilter, UserPrincipal,
│       │                   #   CustomPermissionEvaluator, SecureTokenGenerator,
│       │                   #   crypto/MfaSecretConverter+Encryptor (TOTP secret encryption),
│       │                   #   oauth/GoogleTokenVerifier (ID-token verification)
│       ├── service/ (+impl)# AuthService, UserService, MfaService, MfaBackupCodeService,
│       │                   #   RoleService, PermissionService, UserRoleService, AuditLogService,
│       │                   #   TokenBlacklistService, RefreshTokenService, EmailService,
│       │                   #   EmailVerificationService, AccountUnlockService, LoginHistoryService
│       ├── payload/        # Request/response DTOs (+ mfa/ request/response types)
│       ├── exception/      # GlobalExceptionHandler + custom exceptions
│       ├── validation/     # @ValidIdentifier, @StrongPassword custom constraints
│       └── utils/          # JwtSecretGenerator, PhoneNumberUtil
│   └── src/main/resources/
│       └── db/migration/   # Flyway: V1__baseline, V2__account_security_tokens,
│                           #   V3__mfa_recovery_codes
│
├── open-api/               # Sample business API (port 8082)
│   └── src/main/java/com/test/open-api/
│       ├── controller/  service/  repository/
│       ├── domain/       # TodoList, TodoItem, Tag, ListShare, TodoComment, ...
│       ├── payload/  enums/  util/
│
└── legacy/spring-jwt-auth/ # Archived, not part of the Gradle build (see its README)
```

---

## Development Workflow

1. **Start dependencies** — make sure PostgreSQL is running and `jwt_auth` exists.
2. **Make changes** — shared code goes in `common-api`; auth logic in `security-api`;
   business features in `open-api`. Keep the dependency direction
   (`open-api → security-api → common-api`) intact to avoid build cycles.
3. **Run tests** — `./gradlew test` (or per module, e.g. `./gradlew :security-api:test`).
   Tests use JUnit 5 (`useJUnitPlatform()`).
4. **Run locally** — `./gradlew :security-api:bootRun`, then exercise endpoints via
   Swagger UI or an HTTP client. Start `open-api` too if you need the business API.
5. **Verify the build** — `./gradlew build` before committing.

### Conventions
- **Lombok** is enabled across all modules — use it for boilerplate (getters,
  builders, constructors).
- **MapStruct** is used for DTO ↔ entity mapping in `common-api` and `security-api`.
- API responses are wrapped using the envelope types in
  `common-api` (`ApiResponse` / `ApiStatus` / `StatusCode`) for a consistent shape.
- Exceptions are translated centrally by `GlobalExceptionHandler` in `security-api`.

---

## Configuration Reference

Per-module config lives in `src/main/resources/application.yml` (profile-agnostic
base) plus `application-{local,dev,test,prod}.yml` (see
[Configuration profiles](#configuration-profiles) above). Key settings (shared
shape across modules):

| Key | Source | Notes |
|-----|--------|-------|
| `server.port` | `${SERVER_PORT}` | Defaults: 8081 (`common-api`, not run directly), 8080 (`security-api`), 8082 (`open-api`) |
| `spring.datasource.url` | `${DB_URL}` | Defaults to `jdbc:postgresql://localhost:5432/jwt_auth` (`jwt_auth_test` under the `test` profile) |
| `spring.flyway.enabled` | `true` | Flyway is the sole schema authority — see `db/migration/` |
| `spring.jpa.hibernate.ddl-auto` | `validate` (all profiles) | Hibernate only verifies the schema at startup; it never creates/alters it |
| `jwt.secret` | `${JWT_SECRET}` | No fallback default outside `local`/`test`. **Required env var in `dev`/`prod`.** |
| `jwt.expiration` | `PT24H` | Access-token lifetime (ISO-8601 duration) |
| `jwt.refresh-token.expiration` | `P7D` | Refresh-token lifetime |
| `mfa.encryption-key` | `${MFA_ENCRYPTION_KEY}` | Base64 AES-256 key encrypting TOTP secrets at rest (`MfaEncryptionConfig`/`MfaSecretConverter`). No fallback outside `local`/`test`. |
| `google.client-ids` | `${GOOGLE_CLIENT_IDS:}` | Comma-separated allowed OAuth client IDs; empty disables Google sign-in |
| `app.frontend-url` | `${FRONTEND_URL}` | Used for CORS / reset, verification, and unlock links |
| `app.account-lock.max-failed-attempts` | `5` | Failed logins before an account is locked |
| `app.account-lock.lock-duration-minutes` | `15` | How long a lockout lasts before it auto-clears |
| `app.account-lock.unlock-token-expiration-minutes` | `60` | TTL of the emailed self-service unlock link |
| `app.email-verification.token-expiration-minutes` | `1440` | TTL of the emailed verification link |
| `app.password-policy.*` | min length 12, upper/lower/digit/special required | Enforced by `@StrongPassword` |
| `cache.permission.ttl-minutes` / `max-size` | `${PERMISSION_CACHE_TTL_MINUTES:5}` / `${PERMISSION_CACHE_MAX_SIZE:10000}` | Caffeine cache bounding staleness of `hasPermission(...)` checks (`CacheConfig`) |
| `spring.mail.*` | `${MAIL_USERNAME}` / `${MAIL_PASSWORD}` | Gmail SMTP — used for password reset, email verification, and account-unlock emails |

Google login is verified via ID token (`security/oauth/GoogleTokenVerifier`) —
there is no cookie-based OAuth2 redirect flow, and the old custom OTP feature has
been removed. See `docs/security-api-state.md` for the current state of this module.

---

## Legacy

`legacy/spring-jwt-auth/` is an earlier standalone JWT-auth practice project,
kept for reference. It is **not** wired into the Gradle build (not in
`settings.gradle`) and is superseded by `security-api`. See its own README.
