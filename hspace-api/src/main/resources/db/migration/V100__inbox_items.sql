-- Quick Capture Inbox, moved from Supabase (public.kori_inbox_items, h-space
-- apps/web/supabase/migrations/20260726120000_inbox_items.sql). Same columns,
-- checks and defaults so rows copy across 1:1 with their ids; see
-- docs/spring-migration-plan.md (Phase 1) for the copy and rollback steps.
--
-- Differences from the Supabase table:
--   * user_id references this database's users (same UUIDs as Supabase's
--     auth.users after the account import), and ownership is enforced in
--     InboxItemService instead of an RLS policy.
--   * goal_id has no foreign key: goals still live in Supabase until they move.
--   * version backs optimistic locking (BaseEntity).

CREATE TABLE inbox_items (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             TEXT CHECK (title IS NULL OR char_length(title) <= 200),
    content           TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 4000),
    item_type         TEXT NOT NULL DEFAULT 'note'
        CHECK (item_type IN ('idea', 'note', 'task', 'activity', 'phrase', 'dev', 'journal')),
    tags              TEXT[] NOT NULL DEFAULT '{}',
    captured_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_at          TIMESTAMPTZ,
    goal_id           UUID,
    status            TEXT NOT NULL DEFAULT 'inbox'
        CHECK (status IN ('inbox', 'processed', 'archived')),
    source            TEXT NOT NULL DEFAULT 'manual'
        CHECK (source IN ('manual', 'voice', 'ai', 'converted')),
    pinned            BOOLEAN NOT NULL DEFAULT FALSE,
    converted_to_type TEXT CHECK (converted_to_type IN ('note', 'task', 'journal')),
    converted_to_id   UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    search            TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED
);

CREATE INDEX idx_inbox_items_user_status_captured
    ON inbox_items (user_id, status, captured_at DESC);
CREATE INDEX idx_inbox_items_user_type_captured
    ON inbox_items (user_id, item_type, captured_at DESC);
CREATE INDEX idx_inbox_items_user_goal
    ON inbox_items (user_id, goal_id) WHERE goal_id IS NOT NULL;
CREATE INDEX idx_inbox_items_search ON inbox_items USING GIN (search);
CREATE INDEX idx_inbox_items_tags ON inbox_items USING GIN (tags);
