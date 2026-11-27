# Inbox cutover kit (P1-R1)

Read-only SQL for moving the Quick Capture Inbox between Supabase
(`kori_inbox_items`) and hub-api (`inbox_items`), and for undoing that move.
The step-by-step runbook is in
[`docs/spring-migration-plan.md`](../../docs/spring-migration-plan.md#inbox-cutover-runbook).
Nothing here writes data. The writes (import, replay) are in the runbook so a person runs
them deliberately, inside a transaction.

| File | Runs on | Answers |
|---|---|---|
| `canonical-checksum.sql` | both (`-v inbox_table=…`) | Per-user count + checksum. Equal output on both sides = same content |
| `import-guard.sql` | Spring, inside the import transaction | Staged ids Spring already saw deleted (must be empty) |
| `delta-upserts.sql` | Spring (`-v since=… -v margin=…`) | Rows created or edited since the cutover, to upsert back on rollback |
| `delta-deletes.sql` | Spring (same variables) | Ids deleted since the cutover (tombstones, hub V4), to delete back on rollback |

Rules:

- **Personal data.** Output from the delta and import files contains personal content. Keep it
  outside every repository, never paste it into chats or tools, and delete it afterwards. The
  checksum output holds only user ids, counts and hashes.
- **Freeze first.** Compare checksums only while both writers are frozen. A checksum taken
  while someone is still writing proves nothing.
- **The checksum excludes `updated_at`** (see the header of `canonical-checksum.sql`).
- These files are exercised by `hub-api` `InboxCutoverSqlIntegrationTest` against a real
  PostgreSQL (psql variables are substituted the same way psql does), so a change here must keep
  that test green.
