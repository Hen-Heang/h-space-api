# Schema Migration Prompt

Use when changing a JPA entity in a way that affects the database schema.
Flyway is the sole schema authority (`docs/database.md`,
`security-api/src/main/resources/db/migration/`) — `ddl-auto` is `validate`
everywhere except `test` (`create-drop`), so Hibernate never auto-creates or
alters the schema. Every schema change is a new Flyway migration file.

```
Change entity {{Entity}} in security-api: {{add/rename/remove column,
change type, add relationship, ...}}.

Steps:
- Add a new `V{n}__{description}.sql` under
  security-api/src/main/resources/db/migration/ — find the current highest
  V number first (`ls security-api/src/main/resources/db/migration/`) and
  increment; never edit an already-applied migration file.
- Write the migration to be safe for existing data: nullable/backfilled
  columns for new NOT NULL fields, explicit rename via add+backfill+drop
  across separate migrations rather than an in-place rename if data must be
  preserved.
- Update the entity + any DTO/mapper (MapStruct) touching the changed field
  to match the new schema exactly — ddl-auto=validate means a mismatch fails
  at startup, not silently.

Considerations:
- CI spins up a fresh jwt_auth_test Postgres container each run
  (.github/workflows/ci.yml) with the test profile's create-drop — it won't
  catch issues that only show up against a database with pre-existing data.
  Call that out explicitly if the change is risky for an existing local/prod
  database.
- Confirm no other repository/service assumes the old shape (grep the field
  name across security-api, and open-api if it's exposed further).

Deliverables:
- The new migration file, applied and verified locally
  (`./gradlew :security-api:bootRun` starts clean against it).
- Updated entity/DTO/mapper.
- A test exercising the new shape via the repository/service layer against
  the real test-profile Postgres (.claude/rules/testing.md) — don't mock it.
- CHANGELOG.md [Unreleased] entry.
```
