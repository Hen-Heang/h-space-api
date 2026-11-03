package com.henheang.financeapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.henheang.financeapi.config.FinanceTime;
import com.henheang.financeapi.support.FinanceApiIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bulk delete / recategorize (API-CONTRACT #14) and description suggestions (#15): Luyra's
 * behaviour, plus ownership - another user's ids are never counted or changed, and another user's
 * rows are never suggested. Synthetic data only.
 */
class FinanceTransactionBulkAndSuggestionIntegrationTest extends FinanceApiIntegrationTest {

    private static final String BASE = "/api/finance/transactions";
    private static final String BULK = BASE + "/bulk";
    private static final String SUGGESTIONS = BASE + "/suggestions";
    private static final String MONTH = "start=2026-11-01&end=2026-12-01";

    private final LocalDate today = LocalDate.now(FinanceTime.ZONE);

    private String create(String token, String date, String type, String description)
            throws Exception {
        return create(token, date, type, description, null);
    }

    private String create(
            String token, String date, String type, String description, String categoryId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("type", type);
        body.put("description", description);
        body.put("amountKrw", 1000);
        body.put("categoryId", categoryId);
        return okData(post(BASE), token, body).path("id").asText();
    }

    private String category(String token, String name, String type) throws Exception {
        return okData(post("/api/finance/categories"), token, Map.of("name", name, "type", type))
                .path("id")
                .asText();
    }

    private JsonNode month(String token) throws Exception {
        return okData(get(BASE + "?" + MONTH), token, null).path("transactions");
    }

    private static Map<String, Object> bulk(String action, List<String> ids) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("ids", ids);
        return body;
    }

    // ---- bulk ------------------------------------------------------------

    @Test
    void bulkRequiresAuthentication() throws Exception {
        mockMvc.perform(post(BULK)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SUGGESTIONS + "?q=x")).andExpect(status().isUnauthorized());
    }

    @Test
    void bulkDeleteRemovesOnlyTheCallersRows() throws Exception {
        String a1 = create(aliceToken, "2026-11-03", "expense", "Test A1");
        String a2 = create(aliceToken, "2026-11-04", "expense", "Test A2");
        String a3 = create(aliceToken, "2026-11-05", "expense", "Test A3");
        String bob = create(bobToken, "2026-11-03", "expense", "Bob B1");
        String missing = UUID.randomUUID().toString();

        send(post(BULK), aliceToken, bulk("delete", List.of(a1, a2, bob, missing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(2));

        JsonNode left = month(aliceToken);
        assertThat(left).hasSize(1);
        assertThat(left.get(0).path("id").asText()).isEqualTo(a3);
        assertThat(month(bobToken)).hasSize(1);
    }

    @Test
    void bulkRecategorizeMovesTheCallersRowsAndCanClearTheCategory() throws Exception {
        String food = category(aliceToken, "Test Food", "expense");
        String a1 = create(aliceToken, "2026-11-03", "expense", "Test A1");
        String a2 = create(aliceToken, "2026-11-04", "expense", "Test A2");
        String bob = create(bobToken, "2026-11-03", "expense", "Bob B1");

        Map<String, Object> body = bulk("recategorize", List.of(a1, a2, bob));
        body.put("categoryId", food);
        send(post(BULK), aliceToken, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(2));
        month(aliceToken)
                .forEach(t -> assertThat(t.path("categoryName").asText()).isEqualTo("Test Food"));
        assertThat(month(bobToken).get(0).path("categoryId").isNull()).isTrue();

        send(
                        post(BULK),
                        aliceToken,
                        "{\"action\":\"recategorize\",\"ids\":[\""
                                + a1
                                + "\"],\"categoryId\":null}")
                .andExpect(jsonPath("$.data.affected").value(1));
        send(get(BASE + "?" + MONTH + "&sort=date_asc"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions[0].categoryId").value(nullValue()))
                .andExpect(jsonPath("$.data.transactions[1].categoryName").value("Test Food"));
    }

    @Test
    void bulkRecategorizeRefusesAnotherUsersCategoryAndAMismatchedType() throws Exception {
        String a1 = create(aliceToken, "2026-11-03", "expense", "Test A1");
        String salary = create(aliceToken, "2026-11-04", "income", "Test salary");
        String bobsCategory = category(bobToken, "Bob Food", "expense");
        String incomeOnly = category(aliceToken, "Test Salary", "income");
        String both = category(aliceToken, "Test Misc", "both");

        Map<String, Object> foreign = bulk("recategorize", List.of(a1));
        foreign.put("categoryId", bobsCategory);
        send(post(BULK), aliceToken, foreign).andExpect(status().isNotFound());

        Map<String, Object> mismatch = bulk("recategorize", List.of(a1, salary));
        mismatch.put("categoryId", incomeOnly);
        send(post(BULK), aliceToken, mismatch)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message")
                                .value(
                                        "Test Salary is an income category, but the selection"
                                                + " includes expenses."));
        month(aliceToken).forEach(t -> assertThat(t.path("categoryId").isNull()).isTrue());

        // A "both" category fits a mixed selection.
        Map<String, Object> mixed = bulk("recategorize", List.of(a1, salary));
        mixed.put("categoryId", both);
        send(post(BULK), aliceToken, mixed).andExpect(jsonPath("$.data.affected").value(2));
    }

    @Test
    void rejectsInvalidBulkRequests() throws Exception {
        String a1 = create(aliceToken, "2026-11-03", "expense", "Test A1");

        send(post(BULK), aliceToken, bulk("delete", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Select at least one transaction"));
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add(UUID.randomUUID().toString());
        }
        send(post(BULK), aliceToken, bulk("delete", tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message")
                                .value("Select at most 200 transactions at a time"));
        send(post(BULK), aliceToken, bulk("delete", List.of(a1, a1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Duplicate transaction"));
        send(post(BULK), aliceToken, bulk("archive", List.of(a1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.action").exists());
        send(post(BULK), aliceToken, "{\"ids\":[\"" + a1 + "\"]}")
                .andExpect(status().isBadRequest());
        // recategorize must say which category (null clears; absent is an error).
        send(post(BULK), aliceToken, bulk("recategorize", List.of(a1)))
                .andExpect(status().isBadRequest());
        send(post(BULK), aliceToken, "{\"action\":\"delete\",\"ids\":[\"not-a-uuid\"]}")
                .andExpect(status().isBadRequest());

        assertThat(month(aliceToken)).hasSize(1);
    }

    // ---- suggestions -----------------------------------------------------

    private JsonNode suggest(String token, String query) throws Exception {
        return okData(get(SUGGESTIONS + "?" + query), token, null);
    }

    @Test
    void ranksByUsesWithPrefixMatchesTripled() throws Exception {
        String day = today.toString();
        for (int i = 0; i < 2; i++) {
            create(aliceToken, day, "expense", "Coffee"); // 2 x 3 = 6
        }
        for (int i = 0; i < 3; i++) {
            create(aliceToken, day, "expense", "Iced coffee"); // 3 x 1 = 3
        }
        for (int i = 0; i < 7; i++) {
            create(aliceToken, day, "expense", "Morning coffee"); // 7 x 1 = 7
        }
        create(aliceToken, day, "expense", "Lunch");

        JsonNode suggestions = suggest(aliceToken, "q=coff");

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.get(0).path("description").asText()).isEqualTo("Morning coffee");
        assertThat(suggestions.get(0).path("uses").asInt()).isEqualTo(7);
        assertThat(suggestions.get(1).path("description").asText()).isEqualTo("Coffee");
        assertThat(suggestions.get(2).path("description").asText()).isEqualTo("Iced coffee");
    }

    @Test
    void takesTypeAndCategoryFromTheLatestUse() throws Exception {
        String older = category(aliceToken, "Test Old", "both");
        String newer = category(aliceToken, "Test New", "both");
        create(aliceToken, today.minusDays(10).toString(), "expense", "Test coffee", older);
        create(aliceToken, today.toString(), "income", "Test coffee", newer);

        send(get(SUGGESTIONS + "?q=test"), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].uses").value(2))
                .andExpect(jsonPath("$.data[0].type").value("income"))
                .andExpect(jsonPath("$.data[0].categoryId").value(newer))
                .andExpect(jsonPath("$.data[0].paymentMethodId").value(nullValue()));
    }

    @Test
    void countsOnlyTheLast365SeoulDaysTheTypeAndTheCaller() throws Exception {
        create(aliceToken, today.minusDays(365).toString(), "expense", "Test edge");
        create(aliceToken, today.minusDays(366).toString(), "expense", "Test too old");
        create(aliceToken, today.toString(), "income", "Test refund");
        create(bobToken, today.toString(), "expense", "Test bob");

        JsonNode all = suggest(aliceToken, "q=test");
        assertThat(all).hasSize(2);
        assertThat(all.findValuesAsText("description"))
                .containsExactlyInAnyOrder("Test edge", "Test refund");

        JsonNode expenses = suggest(aliceToken, "q=test&type=expense");
        assertThat(expenses.findValuesAsText("description")).containsExactly("Test edge");
    }

    @Test
    void returnsAtMostSixAndMatchesWildcardsLiterally() throws Exception {
        String day = today.toString();
        for (int i = 0; i < 8; i++) {
            create(aliceToken, day, "expense", "Test item " + i);
        }
        create(aliceToken, day, "expense", "Test 100% juice");

        assertThat(suggest(aliceToken, "q=test")).hasSize(6);
        JsonNode percent = okData(get(SUGGESTIONS).param("q", "100%"), aliceToken, null);
        assertThat(percent.findValuesAsText("description")).containsExactly("Test 100% juice");
        assertThat(suggest(aliceToken, "q=test_item")).isEmpty();
    }

    @Test
    void rejectsInvalidSuggestionQueries() throws Exception {
        send(get(SUGGESTIONS), aliceToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("q is required"));
        send(get(SUGGESTIONS).param("q", "   "), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(get(SUGGESTIONS + "?q=" + "x".repeat(101)), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(get(SUGGESTIONS + "?q=x&type=transfer"), aliceToken, null)
                .andExpect(status().isBadRequest());
    }
}
