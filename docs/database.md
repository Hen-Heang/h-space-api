# Database

- PostgreSQL, DB name `jwt_auth` (test profile uses `jwt_auth_test`), default
  local creds `postgres`/`123`.
- Schema is managed by **Flyway** (`security-api/src/main/resources/db/migration/`,
  currently `V1__baseline.sql`, `V2__account_security_tokens.sql`,
  `V3__mfa_recovery_codes.sql`, `V4__username_and_email_login_codes.sql`,
  `V5__signup_invites.sql`, `V6__mfa_last_used_step.sql`,
  `V7__refresh_token_families.sql`) — migrations run
  automatically on startup.
  In `security-api`, `spring.jpa.hibernate.ddl-auto` is `validate` in every
  profile, `test` included — Hibernate never creates/alters the schema
  itself, it only checks it matches, so tests run against the real Flyway
  schema. A schema change means adding a new `V{n}__description.sql` file,
  not relying on Hibernate auto-sync.
- `api-core` tests use `create-drop`.
- `hub-api` (the deployed app) holds two more migration sets besides
  `security-api`'s, each with its **own** history table so their version
  numbers never collide: hub's (`hub-api/src/main/resources/db/hub-migration/`,
  currently `V1__vault.sql`, `V2__inbox_items.sql`, `V3__app_users.sql` (Auth V2
  H4, proposed: `inbox_items.user_id` now references `app_users`, see
  `docs/auth-v2.md`); `flyway_hub_schema_history`)
  and finance's (`hub-api/src/main/resources/db/finance-migration/`, currently
  `V1__finance_lookups.sql`, `V2__finance_transactions.sql` — V2 needs
  PostgreSQL 15+ for a column-list `ON DELETE SET NULL`;
  `flyway_finance_schema_history`). Their tables live in the same database and
  may reference `users(id)`. At start-up `HubFlywayConfig` runs all three in
  order — security-api's, then hub's, then finance's — in every profile,
  prod included: hub-api is the only app that deploys. Tests run in schema
  `hub_api_test`. (Until 2026-11-26 finance was a separate `finance-api` app
  testing in `finance_api_test`, and prod skipped security's migrations in the
  product apps; there was also a sample `open-api` testing in `open_api_test`.
  Both local schemas are unused now.)
- `engineering-api` keeps everything in its **own schema** `engineering`
  (`ENGINEERING_DB_SCHEMA`), with its own migrations
  (`engineering-api/src/main/resources/db/engineering-migration/`, currently
  `V1__engineering_schema.sql`) and history table
  `flyway_engineering_schema_history` inside that schema. It never runs or
  references `security-api`'s migrations or `users`, and in production it has
  its own Neon project. Tests run in schema `engineering_api_test`. See
  `docs/engineering-api.md`.
- `jwt.secret` has a fallback default only in `local`/`dev`/`test` profiles
  for convenience; `prod` has no fallback and fails fast at startup if
  `JWT_SECRET` isn't set — externalize via env var for anything beyond local,
  never commit a real secret.
- Production uses an external managed PostgreSQL (Neon or Supabase) through
  `DB_URL`; `application-prod.yml` requires TLS (`sslmode=require` by
  default) and sizes the Hikari pool at 5 / min-idle 0, closing idle
  connections after 60s so Neon can scale to zero (a kept idle connection
  gets a keepalive ping every 2 minutes, which stops it). Flyway migrates
  through that pool at startup, so `DB_URL` must be a session endpoint, not
  a transaction-mode pooler — see `docs/deploy-vps.md` §1.
