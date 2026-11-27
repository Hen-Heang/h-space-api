package com.henheang.hubapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.henheang.hubapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hubapi.payload.inbox.InboxItemRequest;
import com.henheang.hubapi.repository.InboxItemRepository;
import com.henheang.hubapi.service.InboxItemService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only SQL in {@code scripts/inbox-cutover/} against the real Flyway schema (vault test
 * plan LED-07/08/09). The files are run as they are, with their psql variables substituted the way
 * psql does it: {@code :"inbox_table"} becomes an identifier and {@code :'since'} / {@code
 * :'margin'} become string literals (bound as parameters here).
 *
 * <p>{@code @Transactional}: everything, including the temporary "Supabase" copy table, is rolled
 * back after each test. The copy uses the same columns as {@code kori_inbox_items}, which is what
 * the checksum compares across the two stores.
 */
@SpringBootTest
@Transactional
class InboxCutoverSqlIntegrationTest {

    private static final Path KIT = Path.of("..", "scripts", "inbox-cutover");

    @Autowired private InboxItemService inboxItemService;
    @Autowired private InboxItemRepository inboxItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void users() {
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    // --- LED-08: canonical checksum ------------------------------------------

    @Test
    void identicalRowsInBothStoresGiveIdenticalChecksums() throws IOException {
        seedVariedItems(alice);
        seedVariedItems(bob);
        copyToSupabaseStandIn();

        Map<String, String> spring = checksums("inbox_items");
        Map<String, String> supabase = checksums("kori_inbox_items");

        assertThat(spring).containsKeys(alice.toString(), bob.toString());
        assertThat(supabase.get(alice.toString())).isEqualTo(spring.get(alice.toString()));
        assertThat(supabase.get(bob.toString())).isEqualTo(spring.get(bob.toString()));
        // Stable: a second run gives the same answer.
        assertThat(checksums("inbox_items")).isEqualTo(spring);
    }

    @Test
    void theSessionTimeZoneDoesNotChangeTheChecksum() throws IOException {
        seedVariedItems(alice);
        jdbcTemplate.execute("SET LOCAL TIME ZONE 'UTC'");
        String utc = checksums("inbox_items").get(alice.toString());
        jdbcTemplate.execute("SET LOCAL TIME ZONE 'Asia/Seoul'");
        String seoul = checksums("inbox_items").get(alice.toString());
        jdbcTemplate.execute("SET LOCAL TIME ZONE 'America/Los_Angeles'");
        String la = checksums("inbox_items").get(alice.toString());

        assertThat(seoul).isEqualTo(utc);
        assertThat(la).isEqualTo(utc);
    }

    @Test
    void anyCopiedColumnChangeIsDetectedPerUser() throws IOException {
        seedVariedItems(alice);
        seedVariedItems(bob);
        copyToSupabaseStandIn();
        String bobBefore = checksums("kori_inbox_items").get(bob.toString());

        // Each of these is a real difference the copy must not hide.
        List<String> changes =
                List.of(
                        // tag order is data
                        "UPDATE kori_inbox_items SET tags = ARRAY['beta','alpha']"
                                + " WHERE tags = ARRAY['alpha','beta'] AND user_id = ?",
                        // NULL is not the empty string
                        "UPDATE kori_inbox_items SET title = '' WHERE title IS NULL AND user_id = ?",
                        // one microsecond
                        "UPDATE kori_inbox_items SET event_at = event_at + interval '1 microsecond'"
                                + " WHERE event_at IS NOT NULL AND user_id = ?",
                        "UPDATE kori_inbox_items SET pinned = NOT pinned WHERE user_id = ?",
                        "UPDATE kori_inbox_items SET goal_id = NULL"
                                + " WHERE goal_id IS NOT NULL AND user_id = ?",
                        // a missing row
                        "DELETE FROM kori_inbox_items WHERE id = (SELECT min(id::text)::uuid"
                                + " FROM kori_inbox_items WHERE user_id = ?)");
        for (String change : changes) {
            String before = checksums("kori_inbox_items").get(alice.toString());
            int touched = jdbcTemplate.update(change, alice);
            assertThat(touched).as(change).isPositive();
            assertThat(checksums("kori_inbox_items").get(alice.toString()))
                    .as(change)
                    .isNotEqualTo(before);
        }
        // Only Alice's changed: per-user isolation.
        assertThat(checksums("kori_inbox_items").get(bob.toString())).isEqualTo(bobBefore);
    }

    @Test
    void updatedAtIsDeliberatelyOutsideTheChecksum() throws IOException {
        seedVariedItems(alice);
        String before = checksums("inbox_items").get(alice.toString());
        jdbcTemplate.update(
                "UPDATE inbox_items SET updated_at = updated_at + interval '1 hour'"
                        + " WHERE user_id = ?",
                alice);
        assertThat(checksums("inbox_items").get(alice.toString())).isEqualTo(before);
    }

    // --- LED-07: delta since the cutover --------------------------------------

    @Test
    void deltaListsCreatesEditsAndDeletesSinceTheCutoverOnly() throws IOException {
        UUID untouched = create(alice, "before T, untouched");
        UUID edited = create(alice, "before T, edited after");
        UUID deleted = create(alice, "before T, deleted after");
        // Move them before the cutover, as if created last week.
        jdbcTemplate.update(
                "UPDATE inbox_items SET created_at = now() - interval '7 days',"
                        + " updated_at = now() - interval '7 days' WHERE user_id = ?",
                alice);
        // The cutover T, from the database clock, as psql would be given it.
        String cutover =
                jdbcTemplate.queryForObject(
                        "SELECT to_char((now() - interval '1 hour') AT TIME ZONE 'UTC',"
                                + " 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')",
                        String.class);

        UUID createdAfter = create(alice, "after T");
        InboxItemPatchRequest patch = new InboxItemPatchRequest();
        patch.setPinned(true);
        inboxItemService.update(alice, edited, patch);
        inboxItemService.delete(alice, deleted);

        Map<String, Object> window = Map.of("since", cutover, "margin", "0 seconds");
        List<UUID> upserts =
                namedJdbc.queryForList(sql("delta-upserts.sql", null), window).stream()
                        .filter(row -> alice.equals(row.get("user_id")))
                        .map(row -> (UUID) row.get("id"))
                        .toList();
        List<UUID> deletes =
                namedJdbc.queryForList(sql("delta-deletes.sql", null), window).stream()
                        .filter(row -> alice.equals(row.get("user_id")))
                        .map(row -> (UUID) row.get("id"))
                        .toList();

        assertThat(upserts).containsExactlyInAnyOrder(createdAfter, edited);
        assertThat(upserts).doesNotContain(untouched, deleted);
        assertThat(deletes).containsExactly(deleted);

        // A margin widens the window back over the untouched row.
        Map<String, Object> wide = Map.of("since", cutover, "margin", "8 days");
        assertThat(
                        namedJdbc.queryForList(sql("delta-upserts.sql", null), wide).stream()
                                .map(row -> row.get("id")))
                .contains(untouched);
    }

    // --- LED-09: import guard -------------------------------------------------

    @Test
    void importGuardReportsStagedIdsAlreadyDeletedInSpring() throws IOException {
        UUID deleted = create(alice, "deleted in Spring");
        inboxItemService.delete(alice, deleted);
        UUID fresh = UUID.randomUUID();
        jdbcTemplate.execute(
                "CREATE TEMP TABLE inbox_stage (id uuid, user_id uuid) ON COMMIT DROP");
        jdbcTemplate.update(
                "INSERT INTO inbox_stage VALUES (?, ?), (?, ?)", deleted, alice, fresh, alice);

        List<UUID> flagged =
                jdbcTemplate.queryForList(sql("import-guard.sql", null)).stream()
                        .map(row -> (UUID) row.get("id"))
                        .toList();

        assertThat(flagged).containsExactly(deleted);
    }

    // --- helpers --------------------------------------------------------------

    // Rows that exercise every awkward value: NULL vs present title/eventAt/goal,
    // ordered tags, empty tags, non-ASCII text and quotes, pinned and conversions.
    private void seedVariedItems(UUID owner) {
        create(owner, "plain note");
        UUID withExtras =
                inboxItemService
                        .create(
                                owner,
                                new InboxItemRequest(
                                        "제목 \"quoted\" | pipe",
                                        "내용 with 'quotes', \\backslash and a\nnewline",
                                        "idea",
                                        List.of("alpha", "beta"),
                                        Instant.parse("2026-11-27T01:02:03.456789Z"),
                                        UUID.randomUUID(),
                                        "voice"))
                        .id();
        InboxItemPatchRequest patch = new InboxItemPatchRequest();
        patch.setPinned(true);
        patch.setStatus(Optional.of("processed"));
        patch.setConvertedToType(Optional.of("task"));
        patch.setConvertedToId(Optional.of(UUID.randomUUID()));
        inboxItemService.update(owner, withExtras, patch);
    }

    // JPA queues an INSERT until the persistence context is flushed (normally at
    // commit). Plain JDBC doesn't trigger that flush, so flush before any SQL
    // here reads the table.
    private UUID create(UUID owner, String content) {
        UUID id =
                inboxItemService
                        .create(
                                owner,
                                new InboxItemRequest(
                                        null, content, "note", List.of(), null, null, null))
                        .id();
        inboxItemRepository.flush();
        return id;
    }

    // A stand-in for Supabase's kori_inbox_items: the same copied columns,
    // without Spring's version/search, filled the way the runbook's import does.
    private void copyToSupabaseStandIn() {
        jdbcTemplate.execute(
                "CREATE TEMP TABLE kori_inbox_items ON COMMIT DROP AS SELECT id, user_id, title,"
                        + " content, item_type, tags, captured_at, event_at, goal_id, status,"
                        + " source, pinned, converted_to_type, converted_to_id, created_at,"
                        + " updated_at FROM inbox_items WHERE user_id IN ('"
                        + alice
                        + "', '"
                        + bob
                        + "')");
    }

    private Map<String, String> checksums(String table) throws IOException {
        return jdbcTemplate.queryForList(sql("canonical-checksum.sql", table)).stream()
                .collect(
                        Collectors.toMap(
                                row -> (String) row.get("user_id"),
                                row -> row.get("items") + ":" + row.get("checksum"),
                                (a, b) -> a,
                                LinkedHashMap::new));
    }

    // psql variable substitution: :"name" -> quoted identifier; :'name' -> a
    // named parameter (bound as a string, like psql's quoted literal).
    private static String sql(String file, String table) throws IOException {
        String text = Files.readString(KIT.resolve(file), StandardCharsets.UTF_8);
        if (table != null) {
            text = text.replace(":\"inbox_table\"", "\"" + table + "\"");
        }
        return text.replace(":'since'", ":since").replace(":'margin'", ":margin");
    }
}
