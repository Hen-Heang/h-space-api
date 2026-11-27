-- Auth V2 / decision H4 (PROPOSED - needs the owner's approval before this
-- merges or runs anywhere but an isolated local test database): a thin local
-- identity anchor for Inbox ownership.
--
-- Why: inbox_items.user_id referenced security-api's users table, so only a
-- Spring account could own an item. Under Auth V2 the product identity is the
-- Supabase Auth user id (the same UUIDs as the legacy users rows after the
-- account import). A Supabase-only user has no users row and must not get one
-- (no second password database), so items now reference app_users instead.
--
-- app_users holds the UUID and a timestamp only: no email, password, role or
-- profile, and it is NOT H-Space admission (that is Supabase's
-- kori_app_access, checked on every request). A row is written only after a
-- caller passed token verification, admission and MFA (InboxItemServiceImpl),
-- or here, for owners of rows that already exist.
--
-- Behaviour change to review: deleting a legacy users row no longer cascades
-- to that user's inbox_items (they stay with the same UUID in app_users).
-- Account deletion is out of Auth V2's scope; see docs/auth-v2.md.

CREATE TABLE app_users (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every current item owner, so the new foreign key holds for existing rows.
INSERT INTO app_users (id)
SELECT DISTINCT user_id FROM inbox_items
ON CONFLICT (id) DO NOTHING;

-- No IF EXISTS: if the constraint isn't named as PostgreSQL names it by
-- default, fail here rather than leave the old users(id) reference in place.
ALTER TABLE inbox_items DROP CONSTRAINT inbox_items_user_id_fkey;
ALTER TABLE inbox_items
    ADD CONSTRAINT inbox_items_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE;
