-- Rollback step: every Spring Inbox item deleted since the cutover
-- (inbox_item_tombstones, hub migration V4). Read-only, Spring database only.
-- Each id must be deleted from Supabase's kori_inbox_items when rolling back.
-- Deleting an id Supabase never had (created and deleted after T) is a no-op.
--
--   psql "$SPRING_DB_URL" -v since='2026-12-01T09:00:00Z' -v margin='10 minutes' -f delta-deletes.sql
--
-- deleted_at is the database clock; the same margin as delta-upserts.sql is
-- used so both halves of the delta cover the same window.
--
-- Tested by InboxCutoverSqlIntegrationTest (LED-07).

SELECT id, user_id, deleted_at
FROM inbox_item_tombstones
WHERE deleted_at >= (:'since')::timestamptz - (:'margin')::interval
ORDER BY id;
