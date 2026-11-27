-- P1-R1: a record of every Inbox delete, so a rollback after the Inbox
-- cutover can replay deletes as well as creates and edits (vault note 11,
-- COPY-03). Without it a hard DELETE leaves no trace, and copying Spring's
-- rows back to Supabase would silently resurrect items the user deleted.
--
-- Privacy: a tombstone holds the deleted item's id, its owner and the time,
-- nothing else. No title, content, tags or links: the item's content is still
-- erased at once, exactly as before. Written only by InboxItemServiceImpl.delete,
-- in the same transaction as the DELETE (both commit or neither does), and only
-- after the caller was verified as the item's owner.
--
-- No foreign key to inbox_items: the row it describes is gone by design.
-- user_id references app_users like inbox_items does; app_users rows are never
-- deleted today, and if one ever is, its tombstones go with it.
--
-- Retention (decision SF-3, proposed): kept until the Supabase table
-- kori_inbox_items is retired (migration plan Phase 6). No purge job yet.

CREATE TABLE inbox_item_tombstones (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "Deletes since T" per user or overall (scripts/inbox-cutover/delta-deletes.sql).
CREATE INDEX idx_inbox_item_tombstones_user_deleted
    ON inbox_item_tombstones (user_id, deleted_at);
CREATE INDEX idx_inbox_item_tombstones_deleted
    ON inbox_item_tombstones (deleted_at);
