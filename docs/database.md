# Database

- PostgreSQL, DB name `jwt_auth` (test profile uses `jwt_auth_test`), default
  local creds `postgres`/`123`.
- Schema is managed by **Flyway** (`security-api/src/main/resources/db/migration/`,
  currently `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`) — migrations run automatically on startup.
  `spring.jpa.hibernate.ddl-auto` is `validate` in every profile except
  `test` (`create-drop`); Hibernate never creates/alters the schema itself,
  it only checks it matches. A schema change means adding a new
  `V{n}__description.sql` file, not relying on Hibernate auto-sync.
- `jwt.secret` has a fallback default only in `local`/`dev`/`test` profiles
  for convenience; `prod` has no fallback and fails fast at startup if
  `JWT_SECRET` isn't set — externalize via env var for anything beyond local,
  never commit a real secret.
