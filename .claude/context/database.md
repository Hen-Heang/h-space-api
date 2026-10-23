# Database Context

PostgreSQL. DB `jwt_auth` locally (`jwt_auth_test` for the `test` profile),
default local creds `postgres`/`123`.

## Flyway is the sole schema authority

- Migrations live in `security-api/src/main/resources/db/migration/`:
  `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`. They run automatically on startup.
- `spring.jpa.hibernate.ddl-auto` is `validate` in `local`/`dev`/`prod` —
  Hibernate checks the entity mapping matches the schema but never
  creates/alters anything. A mismatch fails fast at startup, it doesn't
  silently patch itself.
- `test` profile uses `create-drop` instead (fresh schema per test run,
  bypasses Flyway) — this means CI/local tests don't exercise the actual
  Flyway migrations, only the current entity mappings. A migration file
  with a bug that only manifests against real migration history (vs. a
  fresh create-drop schema) won't be caught by the test suite.
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
`postgres:16` service container (not against Flyway-applied history — the
`test` profile's `create-drop` means Flyway migrations themselves aren't
exercised in CI as of this writing).
