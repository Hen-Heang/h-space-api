# Database

- PostgreSQL, DB name `jwt_auth` (test profile uses `jwt_auth_test`), default
  local creds `postgres`/`123`.
- Schema is managed by **Flyway** (`security-api/src/main/resources/db/migration/`,
  currently `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`) — migrations run automatically on startup.
  In `security-api`, `spring.jpa.hibernate.ddl-auto` is `validate` in every
  profile, `test` included — Hibernate never creates/alters the schema
  itself, it only checks it matches, so tests run against the real Flyway
  schema. A schema change means adding a new `V{n}__description.sql` file,
  not relying on Hibernate auto-sync.
- `open-api` and `common-api` tests use `create-drop`. They share
  `jwt_auth_test` with `security-api`, so `open-api`'s tests run in their own
  schema (`open_api_test`, set in its `application-test.yml`) — otherwise its
  create-drop would drop `security-api`'s tables in `public` and fail the next
  `security-api` test run on a fresh database.
- `jwt.secret` has a fallback default only in `local`/`dev`/`test` profiles
  for convenience; `prod` has no fallback and fails fast at startup if
  `JWT_SECRET` isn't set — externalize via env var for anything beyond local,
  never commit a real secret.
