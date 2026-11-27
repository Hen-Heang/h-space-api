-- Rollback step: every Spring Inbox row created or edited since the cutover.
-- Read-only, Spring database only. The output is what must be upserted back
-- into Supabase (INSERT ... ON CONFLICT (id) DO UPDATE), so over-including a
-- row is harmless and missing one is not.
--
--   psql "$SPRING_DB_URL" -v since='2026-12-01T09:00:00Z' -v margin='10 minutes' -f delta-upserts.sql
--
-- since  = the cutover time T, recorded from the database clock when the web
--          app switched to Spring.
-- margin = a safety window before T. created_at/updated_at are set by the
--          application's clock (Spring Data auditing), not the database's, so
--          a small skew between the two must not drop a row.
--
-- Deletes are in delta-deletes.sql. Tested by InboxCutoverSqlIntegrationTest (LED-07).

SELECT
    id, user_id, title, content, item_type, tags, captured_at, event_at, goal_id,
    status, source, pinned, converted_to_type, converted_to_id, created_at, updated_at
FROM inbox_items
WHERE created_at >= (:'since')::timestamptz - (:'margin')::interval
   OR updated_at >= (:'since')::timestamptz - (:'margin')::interval
ORDER BY id;
