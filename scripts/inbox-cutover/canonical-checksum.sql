-- Per-user count and canonical checksum of Inbox rows. Read-only. Run the SAME
-- file on both stores and compare the output line by line; every line must match.
--
--   Supabase: psql "$SUPABASE_DB_URL" -v inbox_table=kori_inbox_items -f canonical-checksum.sql
--   Spring:   psql "$SPRING_DB_URL"   -v inbox_table=inbox_items      -f canonical-checksum.sql
--
-- What makes it canonical (identical rows give identical output on any
-- server, client or session setting):
--   * Every copied column, by name, in a fixed order. Spring-only columns
--     (version, search) are not read.
--   * Each row is encoded as a jsonb array, whose text form is fixed by
--     PostgreSQL: strings are escaped, so no delimiter can collide; SQL NULL
--     becomes json null, which differs from '' and from the string 'null';
--     booleans are true/false; tags keep their array order (order is data).
--   * Timestamps are converted to UTC and printed with all six microsecond
--     digits, so the session TimeZone and DateStyle can't change the text.
--   * UUIDs are printed as lowercase text.
--   * Rows are combined in uuid order (byte order, collation-independent), and
--     users are listed in uuid order.
--
-- updated_at is deliberately NOT part of the checksum: it is bookkeeping that a
-- database trigger may rewrite on replay (e.g. an updated_at trigger on the
-- Supabase table), which would make equal content look different. Check
-- Supabase's triggers on kori_inbox_items (read-only) before relying on
-- created_at being preserved by a replay.
--
-- Tested by hub-api InboxCutoverSqlIntegrationTest (LED-08).

SELECT
    user_id::text                               AS user_id,
    count(*)                                    AS items,
    md5(string_agg(row_hash, '' ORDER BY id))   AS checksum
FROM (
    SELECT
        id,
        user_id,
        md5(jsonb_build_array(
            id::text,
            user_id::text,
            title,
            content,
            item_type,
            to_jsonb(tags),
            to_char(captured_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
            to_char(event_at    AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
            goal_id::text,
            status,
            source,
            pinned,
            converted_to_type,
            converted_to_id::text,
            to_char(created_at  AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
        )::text) AS row_hash
    FROM :"inbox_table"
) AS canonical_rows
GROUP BY user_id
ORDER BY user_id;
