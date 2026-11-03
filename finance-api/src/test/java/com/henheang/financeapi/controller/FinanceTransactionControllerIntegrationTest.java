package com.henheang.financeapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.henheang.financeapi.support.FinanceApiIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Transactions over HTTP: Luyra's Transaction contract, BigDecimal money rules, ownership of the
 * transaction and of the category / payment method it references, and the list's filters, half-open
 * dates and pagination. Synthetic data only.
 */
class FinanceTransactionControllerIntegrationTest extends FinanceApiIntegrationTest {

    private static final String BASE = "/api/finance/transactions";
    private static final String CATEGORIES = "/api/finance/categories";
    private static final String PAYMENT_METHODS = "/api/finance/payment-methods";

    private static Map<String, Object> krw(String date, String description, long amountKrw) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date);
        body.put("type", "expense");
        body.put("description", description);
        body.put("amountKrw", amountKrw);
        return body;
    }

    private String create(String token, Map<String, Object> body) throws Exception {
        return okData(post(BASE), token, body).path("id").asText();
    }

    private String category(String token, String name, String type) throws Exception {
        return okData(post(CATEGORIES), token, Map.of("name", name, "type", type))
                .path("id")
                .asText();
    }

    private String paymentMethod(String token, String name) throws Exception {
        return okData(post(PAYMENT_METHODS), token, Map.of("name", name)).path("id").asText();
    }

    private static MockHttpServletRequestBuilder list(String query) {
        return get(BASE + "?" + query);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    // ---- authentication and contract -------------------------------------

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "?start=2026-11-01&end=2026-12-01"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAKrwTransactionWithExactlyLuyrasFields() throws Exception {
        String categoryId = category(aliceToken, "Test Food", "expense");
        String methodId = paymentMethod(aliceToken, "Test Card");
        Map<String, Object> body = krw("2026-11-03", "  Test lunch  ", 12000);
        body.put("categoryId", categoryId);
        body.put("paymentMethodId", methodId);
        body.put("note", "  synthetic  ");

        JsonNode data = okData(post(BASE), aliceToken, body);

        assertThat(fieldNames(data))
                .containsExactlyInAnyOrder(
                        "id",
                        "date",
                        "type",
                        "categoryId",
                        "categoryName",
                        "categoryIcon",
                        "categoryColor",
                        "description",
                        "amountKrw",
                        "currency",
                        "originalAmount",
                        "exchangeRate",
                        "paymentMethodId",
                        "paymentMethodName",
                        "note",
                        "createdAt",
                        "updatedAt");
        assertThat(data.path("date").asText()).isEqualTo("2026-11-03");
        assertThat(data.path("description").asText()).isEqualTo("Test lunch");
        assertThat(data.path("note").asText()).isEqualTo("synthetic");
        assertThat(data.path("amountKrw").isNumber()).isTrue();
        assertThat(data.path("amountKrw").decimalValue()).isEqualByComparingTo("12000");
        assertThat(data.path("currency").asText()).isEqualTo("KRW");
        assertThat(data.path("originalAmount").isNull()).isTrue();
        assertThat(data.path("exchangeRate").isNull()).isTrue();
        assertThat(data.path("categoryName").asText()).isEqualTo("Test Food");
        assertThat(data.path("paymentMethodName").asText()).isEqualTo("Test Card");
        assertThat(data.path("createdAt").asText()).isNotBlank();
    }

    @Test
    void computesKrwForUsdOnTheServerAndIgnoresTheClientFigure() throws Exception {
        Map<String, Object> body = krw("2026-11-03", "Test subscription", 1);
        body.put("currency", "USD");
        body.put("originalAmount", 12.30);
        body.put("exchangeRate", 1388.5);

        send(post(BASE), aliceToken, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.amountKrw").value(17079)) // 17078.55
                .andExpect(jsonPath("$.data.originalAmount").value(12.3))
                .andExpect(jsonPath("$.data.exchangeRate").value(1388.5));
    }

    @Test
    void roundsAnExactHalfWonUpToTheNextWon() throws Exception {
        // $156.25 x 1386.1408 = 216584.5 exactly (Luyra's double gives 216584).
        Map<String, Object> body = krw("2026-11-03", "Test golden", 1);
        body.put("currency", "USD");
        body.put("originalAmount", new java.math.BigDecimal("156.25"));
        body.put("exchangeRate", new java.math.BigDecimal("1386.1408"));

        send(post(BASE), aliceToken, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountKrw").value(216585));
    }

    // ---- validation ------------------------------------------------------

    @Test
    void rejectsInvalidBodies() throws Exception {
        Map<String, Object> noAmount = krw("2026-11-03", "Test", 1);
        noAmount.remove("amountKrw");
        send(post(BASE), aliceToken, noAmount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Amount is required"));

        send(
                        post(BASE),
                        aliceToken,
                        "{\"date\":\"2026-11-03\",\"type\":\"expense\","
                                + "\"description\":\"Test\",\"amountKrw\":1000.5}")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message").value("Amount must be a whole number of won"));

        Map<String, Object> usdNoRate = krw("2026-11-03", "Test", 1);
        usdNoRate.put("currency", "USD");
        usdNoRate.put("originalAmount", 10);
        send(post(BASE), aliceToken, usdNoRate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Exchange rate is required"));

        Map<String, Object> negative = krw("2026-11-03", "Test", -5);
        send(post(BASE), aliceToken, negative)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.amountKrw").exists());

        Map<String, Object> badType = krw("2026-11-03", "Test", 1);
        badType.put("type", "transfer");
        send(post(BASE), aliceToken, badType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.type").exists());

        send(post(BASE), aliceToken, krw("2026-11-03", "   ", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.description").exists());
        send(post(BASE), aliceToken, krw("2026-11-03", "x".repeat(201), 1))
                .andExpect(status().isBadRequest());

        Map<String, Object> longNote = krw("2026-11-03", "Test", 1);
        longNote.put("note", "n".repeat(501));
        send(post(BASE), aliceToken, longNote).andExpect(status().isBadRequest());

        Map<String, Object> badCurrency = krw("2026-11-03", "Test", 1);
        badCurrency.put("currency", "EUR");
        send(post(BASE), aliceToken, badCurrency).andExpect(status().isBadRequest());

        // Impossible date, explicit null currency, malformed JSON: all 400.
        send(post(BASE), aliceToken, krw("2026-02-30", "Test", 1))
                .andExpect(status().isBadRequest());
        send(
                        post(BASE),
                        aliceToken,
                        "{\"date\":\"2026-11-03\",\"type\":\"expense\","
                                + "\"description\":\"Test\",\"amountKrw\":1,\"currency\":null}")
                .andExpect(status().isBadRequest());
        send(post(BASE), aliceToken, "{not json").andExpect(status().isBadRequest());

        send(list("start=2026-11-01&end=2026-12-01"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(0)));
    }

    @Test
    void refusesACategoryWhoseTypeDoesNotFit() throws Exception {
        String incomeOnly = category(aliceToken, "Test Salary", "income");
        String both = category(aliceToken, "Test Misc", "both");

        Map<String, Object> body = krw("2026-11-03", "Test", 1000);
        body.put("categoryId", incomeOnly);
        send(post(BASE), aliceToken, body)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message")
                                .value(
                                        "Test Salary is an income category, so it can't be used"
                                                + " for an expense."));

        body.put("categoryId", both);
        send(post(BASE), aliceToken, body).andExpect(status().isOk());
    }

    // ---- ownership -------------------------------------------------------

    @Test
    void refusesAnotherUsersCategoryOrPaymentMethod() throws Exception {
        String bobsCategory = category(bobToken, "Bob Food", "expense");
        String bobsMethod = paymentMethod(bobToken, "Bob Card");

        Map<String, Object> withCategory = krw("2026-11-03", "Test", 1000);
        withCategory.put("categoryId", bobsCategory);
        send(post(BASE), aliceToken, withCategory).andExpect(status().isNotFound());

        Map<String, Object> withMethod = krw("2026-11-03", "Test", 1000);
        withMethod.put("paymentMethodId", bobsMethod);
        send(post(BASE), aliceToken, withMethod).andExpect(status().isNotFound());

        send(list("start=2026-11-01&end=2026-12-01"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(0)));
    }

    @Test
    void anotherUsersTransactionAnswers404AndIsLeftUnchanged() throws Exception {
        String id = create(aliceToken, krw("2026-11-03", "Alice only", 5000));

        send(patch(BASE + "/" + id), bobToken, krw("2026-11-03", "Hijacked", 1))
                .andExpect(status().isNotFound());
        send(delete(BASE + "/" + id), bobToken, null).andExpect(status().isNotFound());
        send(list("start=2026-11-01&end=2026-12-01"), bobToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(0)));

        send(list("start=2026-11-01&end=2026-12-01"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].description").value("Alice only"))
                .andExpect(jsonPath("$.data.transactions[0].amountKrw").value(5000));
    }

    @Test
    void anUpdateRefusesAnotherUsersCategory() throws Exception {
        String id = create(aliceToken, krw("2026-11-03", "Test", 1000));
        String bobsCategory = category(bobToken, "Bob Food", "expense");

        Map<String, Object> body = krw("2026-11-03", "Test", 1000);
        body.put("categoryId", bobsCategory);
        send(patch(BASE + "/" + id), aliceToken, body).andExpect(status().isNotFound());
    }

    // ---- update and delete -----------------------------------------------

    @Test
    void anUpdateReplacesEveryField() throws Exception {
        String categoryId = category(aliceToken, "Test Food", "expense");
        Map<String, Object> original = krw("2026-11-03", "Test lunch", 12000);
        original.put("categoryId", categoryId);
        original.put("note", "first");
        String id = create(aliceToken, original);

        Map<String, Object> replacement = krw("2026-11-04", "Test dinner", 15000);
        replacement.put("type", "income");
        send(patch(BASE + "/" + id), aliceToken, replacement)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.date").value("2026-11-04"))
                .andExpect(jsonPath("$.data.type").value("income"))
                .andExpect(jsonPath("$.data.amountKrw").value(15000))
                .andExpect(jsonPath("$.data.categoryId").value(nullValue()))
                .andExpect(jsonPath("$.data.categoryName").value(nullValue()))
                .andExpect(jsonPath("$.data.note").value(nullValue()));

        // KRW -> USD on the same row.
        Map<String, Object> toUsd = krw("2026-11-04", "Test dinner", 1);
        toUsd.put("currency", "USD");
        toUsd.put("originalAmount", 10);
        toUsd.put("exchangeRate", 1390);
        send(patch(BASE + "/" + id), aliceToken, toUsd)
                .andExpect(jsonPath("$.data.amountKrw").value(13900))
                .andExpect(jsonPath("$.data.originalAmount").value(10));
    }

    @Test
    void deletesAndAnswersWithTheId() throws Exception {
        String id = create(aliceToken, krw("2026-11-03", "Test", 1000));

        send(delete(BASE + "/" + id), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        send(list("start=2026-11-01&end=2026-12-01"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(0)));
        send(delete(BASE + "/" + id), aliceToken, null).andExpect(status().isNotFound());
    }

    @Test
    void aCategoryOrPaymentMethodInUseCannotBeDeleted() throws Exception {
        String categoryId = category(aliceToken, "Test Food", "expense");
        String methodId = paymentMethod(aliceToken, "Test Card");
        Map<String, Object> body = krw("2026-11-03", "Test", 1000);
        body.put("categoryId", categoryId);
        body.put("paymentMethodId", methodId);
        String first = create(aliceToken, body);
        create(aliceToken, body);

        send(delete(CATEGORIES + "/" + categoryId), aliceToken, null)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status.message")
                                .value(
                                        "\"Test Food\" is still used by 2 transactions. Reassign"
                                                + " or delete those first."));
        send(delete(PAYMENT_METHODS + "/" + methodId), aliceToken, null)
                .andExpect(status().isConflict());

        // Once nothing references them, they delete.
        send(patch(BASE + "/" + first), aliceToken, krw("2026-11-03", "Test", 1000))
                .andExpect(status().isOk());
        send(delete(CATEGORIES + "/" + categoryId), aliceToken, null)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status.message")
                                .value(
                                        "\"Test Food\" is still used by 1 transaction. Reassign"
                                                + " or delete those first."));
    }

    // ---- list: range, filters, sort, pages -------------------------------

    @Test
    void listsAHalfOpenDateRange() throws Exception {
        create(aliceToken, krw("2026-10-31", "Before", 1));
        create(aliceToken, krw("2026-11-01", "First day", 2));
        create(aliceToken, krw("2026-11-30", "Last day", 3));
        create(aliceToken, krw("2026-12-01", "End date (excluded)", 4));

        send(list("start=2026-11-01&end=2026-12-01"), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions", hasSize(2)))
                .andExpect(jsonPath("$.data.transactions[0].description").value("Last day"))
                .andExpect(jsonPath("$.data.transactions[1].description").value("First day"))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void filtersByTypeCategoryPaymentMethodAmountAndSearch() throws Exception {
        String food = category(aliceToken, "Test Food", "expense");
        String card = paymentMethod(aliceToken, "Test Card");

        Map<String, Object> lunch = krw("2026-11-03", "Lunch 100% beef", 12000);
        lunch.put("categoryId", food);
        lunch.put("paymentMethodId", card);
        create(aliceToken, lunch);
        create(aliceToken, krw("2026-11-04", "Lunch_box", 8000));
        Map<String, Object> salary = krw("2026-11-05", "Salary", 3000000);
        salary.put("type", "income");
        create(aliceToken, salary);
        create(bobToken, krw("2026-11-03", "Lunch (Bob)", 9000));

        String range = "start=2026-11-01&end=2026-12-01";
        send(list(range + "&type=income"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].description").value("Salary"));
        send(list(range + "&categoryId=" + food), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].categoryName").value("Test Food"));
        send(list(range + "&paymentMethodId=" + card), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)))
                .andExpect(jsonPath("$.data.transactions[0].paymentMethodName").value("Test Card"));
        send(list(range + "&amountMin=8000&amountMax=12000"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(2)));
        send(list(range + "&search=  LUNCH  "), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(2)));
        // % and _ are literal, not wildcards.
        send(
                        get(BASE)
                                .param("start", "2026-11-01")
                                .param("end", "2026-12-01")
                                .param("search", "100%"),
                        aliceToken,
                        null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)));
        // As a wildcard, _ would match the space in "Lunch 100% beef".
        send(list(range + "&search=lunch_1"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(0)));
        send(list(range + "&search=lunch_"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions", hasSize(1)));
    }

    @Test
    void sortsByAmountAndDate() throws Exception {
        create(aliceToken, krw("2026-11-02", "Mid", 5000));
        create(aliceToken, krw("2026-11-01", "Big", 9000));
        create(aliceToken, krw("2026-11-03", "Small", 1000));

        String range = "start=2026-11-01&end=2026-12-01";
        send(list(range + "&sort=amount_desc"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions[0].description").value("Big"))
                .andExpect(jsonPath("$.data.transactions[2].description").value("Small"));
        send(list(range + "&sort=amount_asc"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions[0].description").value("Small"));
        send(list(range + "&sort=date_asc"), aliceToken, null)
                .andExpect(jsonPath("$.data.transactions[0].description").value("Big"))
                .andExpect(jsonPath("$.data.transactions[2].description").value("Small"));
    }

    @Test
    void pagesTwentyAtATimeWithoutGapsOrRepeats() throws Exception {
        // 21 rows on the same date, so only created_at and id order them.
        for (int i = 0; i < 21; i++) {
            create(aliceToken, krw("2026-11-03", "Row " + i, 1000 + i));
        }
        String range = "start=2026-11-01&end=2026-12-01";

        JsonNode first = okData(list(range), aliceToken, null);
        JsonNode second = okData(list(range + "&page=1"), aliceToken, null);
        JsonNode third = okData(list(range + "&page=2"), aliceToken, null);

        assertThat(first.path("transactions").size()).isEqualTo(20);
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(second.path("transactions").size()).isEqualTo(1);
        assertThat(second.path("hasMore").asBoolean()).isFalse();
        assertThat(third.path("transactions").size()).isZero();

        Set<String> ids = new HashSet<>();
        first.path("transactions").forEach(t -> ids.add(t.path("id").asText()));
        second.path("transactions").forEach(t -> ids.add(t.path("id").asText()));
        assertThat(ids).hasSize(21);
    }

    @Test
    void rejectsInvalidListQueries() throws Exception {
        send(get(BASE + "?end=2026-12-01"), aliceToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.start").exists());
        send(list("start=2026-11-01&end=2026-12-01&sort=newest"), aliceToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.sort").exists());
        send(list("start=2026-11-01&end=2026-12-01&categoryId=food"), aliceToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.categoryId").exists());
        send(list("start=2026-11-01&end=2026-12-01&page=-1"), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(list("start=2026-11-01&end=2026-12-01&amountMin=-1"), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(list("start=2026-02-30&end=2026-03-01"), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(list("start=2026-11-01&end=2026-12-01&search=" + "s".repeat(201)), aliceToken, null)
                .andExpect(status().isBadRequest());
    }
}
