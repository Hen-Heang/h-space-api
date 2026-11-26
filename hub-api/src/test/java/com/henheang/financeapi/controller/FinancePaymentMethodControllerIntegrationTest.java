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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Payment methods over HTTP: contract with Luyra's PaymentMethod type and ownership. */
class FinancePaymentMethodControllerIntegrationTest extends FinanceApiIntegrationTest {

    private static final String BASE = "/api/finance/payment-methods";

    private String create(String token, String name) throws Exception {
        return okData(post(BASE), token, Map.of("name", name)).path("id").asText();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsWithExactlyThePaymentMethodFields() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "  Test Card  ");
        body.put("icon", "💳");
        JsonNode data = okData(post(BASE), aliceToken, body);

        assertThat(data.size()).isEqualTo(3);
        assertThat(data.path("id").asText()).isNotBlank();
        assertThat(data.path("name").asText()).isEqualTo("Test Card");
        assertThat(data.path("icon").asText()).isEqualTo("💳");
    }

    @Test
    void listsOnlyTheCallersPaymentMethodsByName() throws Exception {
        create(aliceToken, "Test Wallet");
        create(aliceToken, "Test Bank");
        create(bobToken, "Bob Card");

        send(get(BASE), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Test Bank"))
                .andExpect(jsonPath("$.data[1].name").value("Test Wallet"));
    }

    @Test
    void validatesAndRejectsDuplicates() throws Exception {
        send(post(BASE), aliceToken, Map.of("name", "")).andExpect(status().isBadRequest());
        send(post(BASE), aliceToken, Map.of("name", "x".repeat(41)))
                .andExpect(status().isBadRequest());

        create(aliceToken, "Test Card");
        send(post(BASE), aliceToken, Map.of("name", "TEST card"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status.message")
                                .value("You already have a payment method named \"Test Card\"."));
        create(bobToken, "Test Card");
    }

    @Test
    void patchesAndClearsTheIcon() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Card");
        body.put("icon", "💳");
        String id = okData(post(BASE), aliceToken, body).path("id").asText();

        send(patch(BASE + "/" + id), aliceToken, Map.of("name", "Test Debit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Debit"))
                .andExpect(jsonPath("$.data.icon").value("💳"));
        send(patch(BASE + "/" + id), aliceToken, "{\"icon\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.icon").value(nullValue()));
        send(patch(BASE + "/" + id), aliceToken, "{}").andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, "{\"name\":null}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUsersPaymentMethodAnswers404() throws Exception {
        String id = create(aliceToken, "Test Card");

        send(patch(BASE + "/" + id), bobToken, Map.of("name", "Hijacked"))
                .andExpect(status().isNotFound());
        send(delete(BASE + "/" + id), bobToken, null).andExpect(status().isNotFound());
        send(get(BASE), aliceToken, null).andExpect(jsonPath("$.data[0].name").value("Test Card"));
    }

    @Test
    void deletesAndAnswersWithTheId() throws Exception {
        String id = create(aliceToken, "Test Card");

        send(delete(BASE + "/" + id), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        send(get(BASE), aliceToken, null).andExpect(jsonPath("$.data", hasSize(0)));
    }
}
