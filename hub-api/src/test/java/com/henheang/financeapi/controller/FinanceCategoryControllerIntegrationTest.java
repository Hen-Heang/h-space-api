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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Categories over HTTP: contract with Luyra's Category type, validation and ownership. */
class FinanceCategoryControllerIntegrationTest extends FinanceApiIntegrationTest {

    private static final String BASE = "/api/finance/categories";

    private String create(String token, Map<String, Object> body) throws Exception {
        return okData(post(BASE), token, body).path("id").asText();
    }

    private static Map<String, Object> category(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        return body;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsWithLuyraDefaultsAndExactlyTheCategoryFields() throws Exception {
        JsonNode data = okData(post(BASE), aliceToken, category("  Test Food  "));

        assertThat(fieldNames(data))
                .containsExactlyInAnyOrder("id", "name", "icon", "color", "type", "spendingClass");
        assertThat(data.path("id").asText()).isNotBlank();
        assertThat(data.path("name").asText()).isEqualTo("Test Food");
        assertThat(data.path("type").asText()).isEqualTo("expense");
        assertThat(data.path("icon").isNull()).isTrue();
        assertThat(data.path("color").isNull()).isTrue();
        assertThat(data.path("spendingClass").isNull()).isTrue();
    }

    @Test
    void createsWithEveryField() throws Exception {
        Map<String, Object> body = category("Test Salary");
        body.put("icon", " 💼 ");
        body.put("color", "#10B981");
        body.put("type", "income");
        body.put("spendingClass", "growth");

        send(post(BASE), aliceToken, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.icon").value("💼"))
                .andExpect(jsonPath("$.data.color").value("#10B981"))
                .andExpect(jsonPath("$.data.type").value("income"))
                .andExpect(jsonPath("$.data.spendingClass").value("growth"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void listsOnlyTheCallersCategoriesByName() throws Exception {
        create(aliceToken, category("Test Rent"));
        create(aliceToken, category("Test Coffee"));
        create(bobToken, category("Bob Only"));

        send(get(BASE), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Test Coffee"))
                .andExpect(jsonPath("$.data[1].name").value("Test Rent"));
        send(get(BASE), bobToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Bob Only"));
    }

    @Test
    void rejectsADuplicateNameIgnoringCaseButOnlyForTheSameUser() throws Exception {
        create(aliceToken, category("Test Food"));

        send(post(BASE), aliceToken, category("test FOOD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status.code").value(40900))
                .andExpect(
                        jsonPath("$.status.message")
                                .value("You already have a category named \"Test Food\"."));
        // Another user may use the same name.
        create(bobToken, category("Test Food"));
    }

    @Test
    void rejectsInvalidCreateRequests() throws Exception {
        send(post(BASE), aliceToken, category("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists());
        send(post(BASE), aliceToken, category("x".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists());

        Map<String, Object> badColor = category("Test A");
        badColor.put("color", "red");
        send(post(BASE), aliceToken, badColor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.color").exists());

        Map<String, Object> badType = category("Test B");
        badType.put("type", "transfer");
        send(post(BASE), aliceToken, badType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.type").exists());

        Map<String, Object> badClass = category("Test C");
        badClass.put("spendingClass", "luxury");
        send(post(BASE), aliceToken, badClass)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.spendingClass").exists());

        Map<String, Object> blankIcon = category("Test D");
        blankIcon.put("icon", "   ");
        send(post(BASE), aliceToken, blankIcon)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.icon").exists());

        Map<String, Object> longIcon = category("Test E");
        longIcon.put("icon", "123456789");
        send(post(BASE), aliceToken, longIcon)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.icon").exists());

        // zod's .default("expense") fills only an absent type, not null.
        send(post(BASE), aliceToken, "{\"name\":\"Test F\",\"type\":null}")
                .andExpect(status().isBadRequest());
        send(post(BASE), aliceToken, "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void patchesOnlyTheGivenFieldsAndClearsExplicitNulls() throws Exception {
        Map<String, Object> body = category("Test Food");
        body.put("icon", "🍜");
        body.put("color", "#10b981");
        body.put("spendingClass", "essential");
        String id = create(aliceToken, body);

        send(patch(BASE + "/" + id), aliceToken, Map.of("type", "both"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("both"))
                .andExpect(jsonPath("$.data.name").value("Test Food"))
                .andExpect(jsonPath("$.data.icon").value("🍜"));

        send(
                        patch(BASE + "/" + id),
                        aliceToken,
                        "{\"icon\":null,\"color\":null,\"spendingClass\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.icon").value(nullValue()))
                .andExpect(jsonPath("$.data.color").value(nullValue()))
                .andExpect(jsonPath("$.data.spendingClass").value(nullValue()))
                .andExpect(jsonPath("$.data.type").value("both"));

        // Renaming to its own name in another case is allowed.
        send(patch(BASE + "/" + id), aliceToken, Map.of("name", " test food "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("test food"));
    }

    @Test
    void rejectsInvalidPatches() throws Exception {
        String id = create(aliceToken, category("Test Food"));
        create(aliceToken, category("Test Rent"));

        send(patch(BASE + "/" + id), aliceToken, "{}").andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, "{\"name\":null}")
                .andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, "{\"type\":null}")
                .andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, Map.of("color", "#12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.color").exists());
        send(patch(BASE + "/" + id), aliceToken, Map.of("name", "TEST RENT"))
                .andExpect(status().isConflict());
    }

    @Test
    void anotherUsersCategoryAnswers404AndIsLeftUnchanged() throws Exception {
        String id = create(aliceToken, category("Test Food"));

        send(patch(BASE + "/" + id), bobToken, Map.of("name", "Hijacked"))
                .andExpect(status().isNotFound());
        send(delete(BASE + "/" + id), bobToken, null).andExpect(status().isNotFound());

        send(get(BASE), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Test Food"));
    }

    @Test
    void deletesAndAnswersWithTheId() throws Exception {
        String id = create(aliceToken, category("Test Food"));

        send(delete(BASE + "/" + id), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        send(get(BASE), aliceToken, null).andExpect(jsonPath("$.data", hasSize(0)));
        send(delete(BASE + "/" + id), aliceToken, null).andExpect(status().isNotFound());
    }
}
