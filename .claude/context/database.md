# Database Context

PostgreSQL. DB `jwt_auth` locally (`jwt_auth_test` for the `test` profile),
default local creds `postgres`/`123`.

## Flyway is the sole schema authority

- Migrations live in `security-api/src/main/resources/db/migration/`:
  `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`. They run automatically on startup.
- `spring.jpa.hibernate.ddl-auto` is `validate` in every `security-api`
  profile, `test` included — Hibernate checks the entity mapping matches the
  schema but never creates/alters anything. A mismatch fails fast at startup,
  it doesn't silently patch itself, and `security-api`'s tests run against
  the real Flyway-applied schema.
- `open-api`/`common-api` tests use `create-drop`. `open-api` scans
  `security-api`'s entities and has its migrations on the classpath, so its
  tests are isolated in the `open_api_test` schema of `jwt_auth_test` —
  without that, its create-drop dropped `security-api`'s tables in `public`
  and the next `security-api` test run failed on a fresh database.
- Any entity change that affects the schema needs a new
  `V{n}__description.sql` file — see `.claude/prompts/migration.md` for the
  step-by-step. Never edit an already-applied migration file; add a new one.

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
