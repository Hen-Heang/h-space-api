# Schema Migration Prompt

Use when changing a JPA entity in a way that affects the database schema.
Flyway is the sole schema authority (`docs/database.md`). In `security-api`,
`finance-api` and `hub-api`, `ddl-auto` is `validate` in every profile,
`test` included — Hibernate never creates or alters the schema, and their
tests run against the real Flyway-applied schema. (Only `open-api` and
`api-core` tests use `create-drop`.) Every schema change is a new Flyway
migration file.

Pick the right migration location — each has its own version sequence:

| Module | Directory | History table |
|---|---|---|
| `security-api` | `security-api/src/main/resources/db/migration/` | `flyway_schema_history` |
| `hub-api` | `hub-api/src/main/resources/db/hub-migration/` | `flyway_hub_schema_history` |
| `finance-api` | see `docs/finance-api.md` before adding any table | — |

```
Change entity {{Entity}} in {{module}}: {{add/rename/remove column,
change type, add relationship, ...}}.

Steps:
- Add a new `V{n}__{description}.sql` in that module's migration directory
  — list it first, take the highest V number and increment; never edit an
  already-applied migration file.
- Write the migration to be safe for existing data: nullable/backfilled
  columns for new NOT NULL fields, add+backfill+drop across separate
  migrations rather than an in-place rename when data must be preserved.
- Update the entity + any DTO/mapper (MapStruct) touching the changed field
  to match the new schema exactly — ddl-auto=validate fails at startup on a
  mismatch, it never patches silently.
- If it touches vault tables, follow `.claude/rules/vault.md` (no new secret
  fields before the security review).

Considerations:
- CI runs the migrations against a fresh `postgres:16` each run, so a broken
  migration fails CI — but CI won't catch problems that only show against
  existing data (locks on big tables, NOT NULL on populated columns,
  constraint violations). Call those out explicitly for prod (Neon); prod
  migrates at startup, take a `pg_dump` first (`docs/deploy-vps.md` §8).
- In prod `hub-api` doesn't run `security-api`'s migrations, so a `hub-api`
  migration that references a new `security-api` column needs
  `security-api` deployed first.
- Grep the field name across all modules for code assuming the old shape.

Deliverables:
- The new migration file; `./gradlew :{{module}}:test` passes against it.
- Updated entity/DTO/mapper.
- A test exercising the new shape via the repository/service layer against
  the real test-profile Postgres (.claude/rules/testing.md) — don't mock it.
- `docs/database.md` migration list updated; CHANGELOG.md [Unreleased] entry.
```
