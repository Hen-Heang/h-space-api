# Database Context

PostgreSQL. DB `jwt_auth` locally (`jwt_auth_test` for the `test` profile),
default local creds `postgres`/`123`.

## Flyway is the sole schema authority

- Migrations live in `security-api/src/main/resources/db/migration/`:
  `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`, `V4__username_and_email_login_codes.sql`,
  `V5__signup_invites.sql`, `V6__mfa_last_used_step.sql`. They run
  automatically on startup.
- `spring.jpa.hibernate.ddl-auto` is `validate` in every `security-api`
  profile, `test` included — Hibernate checks the entity mapping matches the
  schema but never creates/alters anything. A mismatch fails fast at startup,
  it doesn't silently patch itself, and `security-api`'s tests run against
  the real Flyway-applied schema.
- `api-core` tests use `create-drop`.
- Any entity change that affects the schema needs a new
  `V{n}__description.sql` file — see `.claude/commands/migration.md` for the
  step-by-step. Never edit an already-applied migration file; add a new one.
- `hub-api` has its own migrations (`hub-api/src/main/resources/db/hub-migration/`,
  history table `flyway_hub_schema_history`) with their own version numbers;
  finance has its own too (`hub-api/src/main/resources/db/finance-migration/`,
  `flyway_finance_schema_history`). `HubFlywayConfig` runs security's, then
  hub's, then finance's, in every profile including `prod` — hub-api is the
  only deployed app. Tests run in schema `hub_api_test`.

## Secrets

- `local`/`dev`/`test` profiles have fallback defaults (DB password `123`,
  placeholder/fixed JWT secrets) for zero-config convenience — fine for
  those profiles, never assume they're safe to reuse elsewhere.
- `prod` has no fallback defaults anywhere — `DB_URL`/`DB_USERNAME`/
  `DB_PASSWORD`/`JWT_SECRET`/`MFA_ENCRYPTION_KEY` must be set via
  environment variables or the app fails to start.
- `application-local.yml` is git-ignored; use it or a git-ignored `.env` for
  real local values instead of committing anything.

## CI

`.github/workflows/ci.yml` runs `./gradlew clean build` against a real
`postgres:16` service container. `security-api`'s tests apply the Flyway
migrations to a fresh database each run, so a broken migration fails CI.
