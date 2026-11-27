package com.henheang.hubapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.hubapi.payload.inbox.InboxItemPatchRequest;
import com.henheang.hubapi.repository.InboxItemRepository;
import com.henheang.hubapi.service.InboxItemService;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P1-R1: a delete and its tombstone commit together or not at all (vault test plan LED-05/06).
 *
 * <p>Deliberately NOT {@code @Transactional}: the point is what really reaches the database when a
 * request's transaction commits or rolls back, and a test-managed transaction would hide that (it
 * rolls everything back at the end). So rows really commit here, and {@link #cleanUp()} removes
 * them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InboxItemDeleteAtomicityIntegrationTest {

    private static final String BASE = "/api/hub/inbox-items";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private InboxItemService inboxItemService;
    @Autowired private InboxItemRepository inboxItemRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private String ownerToken;

    @BeforeEach
    void createOwner() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("ledger-" + suffix + "@example.com");
        user.setUsername("l" + suffix);
        user.setName("Ledger Tester");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user = userRepository.saveAndFlush(user);
        ownerId = user.getId();
        ownerToken = jwtTokenProvider.generateToken(user);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS led05_fail ON inbox_item_tombstones");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS led05_fail()");
        jdbcTemplate.update("DELETE FROM inbox_item_tombstones WHERE user_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM inbox_items WHERE user_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM app_users WHERE id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", ownerId);
    }

    // LED-05: the tombstone INSERT fails after the DELETE has already been sent
    // (flushed). The request fails and its transaction rolls back BOTH: the item
    // is still there, and there is no tombstone.
    @Test
    void aFailingTombstoneRollsBackTheDelete() throws Exception {
        UUID id = createItem("must survive");
        // A real database failure, only for this one id: a trigger that rejects
        // its tombstone. Removed again in cleanUp().
        jdbcTemplate.execute(
                "CREATE FUNCTION led05_fail() RETURNS trigger LANGUAGE plpgsql AS $$"
                        + " BEGIN IF NEW.id = '"
                        + id
                        + "' THEN RAISE EXCEPTION 'LED-05 forced failure'; END IF;"
                        + " RETURN NEW; END $$");
        jdbcTemplate.execute(
                "CREATE TRIGGER led05_fail BEFORE INSERT ON inbox_item_tombstones"
                        + " FOR EACH ROW EXECUTE FUNCTION led05_fail()");

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().is5xxServerError());

        assertThat(itemExists(id)).isTrue();
        assertThat(tombstoneCount(id)).isZero();
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    // LED-06a (deterministic): the item is edited by someone else after this
    // transaction read it. The version-checked DELETE removes nothing, so the
    // delete fails with the optimistic-lock exception the API has always mapped
    // to 409 - before any tombstone is written - and nothing changes.
    @Test
    void aStaleDeleteFailsBeforeAnyTombstoneIsWritten() throws Exception {
        UUID id = createItem("edited concurrently");
        TransactionTemplate request = new TransactionTemplate(transactionManager);
        TransactionTemplate otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(
                        () ->
                                request.executeWithoutResult(
                                        tx -> {
                                            // This request has read version 0 ...
                                            inboxItemRepository.findById(id).orElseThrow();
                                            // ... another request commits an edit (version 1) ...
                                            otherRequest.executeWithoutResult(
                                                    tx2 -> {
                                                        InboxItemPatchRequest patch =
                                                                new InboxItemPatchRequest();
                                                        patch.setPinned(true);
                                                        inboxItemService.update(ownerId, id, patch);
                                                    });
                                            // ... and now this request deletes what it read.
                                            inboxItemService.delete(ownerId, id);
                                        }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(itemExists(id)).isTrue();
        assertThat(tombstoneCount(id)).isZero();
    }

    // LED-06b: two real concurrent deletes of the same item, several rounds.
    // Whatever the interleaving: exactly one succeeds, the other is the existing
    // 404 (it read after the commit) or 409 (it read before it), never a 500; the
    // item is gone and there is exactly one tombstone.
    @Test
    void concurrentDeletesLeaveExactlyOneTombstone() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                UUID id = createItem("race " + round);
                CountDownLatch start = new CountDownLatch(1);
                Callable<Integer> deleteIt =
                        () -> {
                            start.await();
                            return mockMvc.perform(
                                            delete(BASE + "/" + id)
                                                    .header(
                                                            "Authorization",
                                                            "Bearer " + ownerToken))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                List<Future<Integer>> results = new ArrayList<>();
                results.add(pool.submit(deleteIt));
                results.add(pool.submit(deleteIt));
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).containsOnlyOnce(200);
                assertThat(statuses).allMatch(s -> s == 200 || s == 404 || s == 409);
                assertThat(itemExists(id)).isFalse();
                assertThat(tombstoneCount(id)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID createItem(String content) throws Exception {
        String json =
                mockMvc.perform(
                                post(BASE)
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "content",
                                                                content,
                                                                "itemType",
                                                                "note"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).path("data").path("id").asText());
    }

    private boolean itemExists(UUID id) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM inbox_items WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private int tombstoneCount(UUID id) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM inbox_item_tombstones WHERE id = ?",
                        Integer.class,
                        id);
        return count == null ? 0 : count;
    }
}
