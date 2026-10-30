package com.henheang.hspaceapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox items over HTTP against the real Flyway schema (test profile). What replaces Supabase RLS
 * is the user-id scoping in the service, so most of these tests are about one user never seeing or
 * changing another user's items.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InboxItemControllerIntegrationTest {

    private static final String BASE = "/api/v1/inbox-items";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void createUsers() {
        aliceToken = jwtTokenProvider.generateToken(saveUser());
        bobToken = jwtTokenProvider.generateToken(saveUser());
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("inbox-" + suffix + "@example.com");
        user.setUsername("u" + suffix);
        user.setName("Inbox Tester");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token,
            Object body)
            throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private String createItem(String token, Map<String, Object> body) throws Exception {
        String json =
                send(post(BASE), token, body)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode data = objectMapper.readTree(json).path("data");
        return data.path("id").asText();
    }

    private Map<String, Object> item(String content, String itemType) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("itemType", itemType);
        return body;
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndReadsBackAnItemWithDefaults() throws Exception {
        Map<String, Object> body = item("  Call the bank about the card  ", "task");
        body.put("title", "  Bank  ");
        body.put("tags", List.of("money", "todo"));
        String id = createItem(aliceToken, body);

        send(get(BASE + "/" + id), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Bank"))
                .andExpect(jsonPath("$.data.content").value("Call the bank about the card"))
                .andExpect(jsonPath("$.data.itemType").value("task"))
                .andExpect(jsonPath("$.data.tags", hasSize(2)))
                .andExpect(jsonPath("$.data.status").value("inbox"))
                .andExpect(jsonPath("$.data.source").value("manual"))
                .andExpect(jsonPath("$.data.pinned").value(false))
                .andExpect(jsonPath("$.data.capturedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void rejectsInvalidCreateRequests() throws Exception {
        send(post(BASE), aliceToken, item("   ", "note"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").exists());
        send(post(BASE), aliceToken, item("x", "recipe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.itemType").exists());

        Map<String, Object> longTitle = item("x", "note");
        longTitle.put("title", "t".repeat(201));
        send(post(BASE), aliceToken, longTitle)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").exists());

        Map<String, Object> badSource = item("x", "note");
        badSource.put("source", "email");
        send(post(BASE), aliceToken, badSource).andExpect(status().isBadRequest());
    }

    @Test
    void listsOnlyTheCallersItemsWithFilters() throws Exception {
        createItem(aliceToken, item("first idea", "idea"));
        String noteId = createItem(aliceToken, item("a note", "note"));
        createItem(bobToken, item("bob's idea", "idea"));
        send(patch(BASE + "/" + noteId), aliceToken, Map.of("status", "archived"))
                .andExpect(status().isOk());

        send(get(BASE), aliceToken, null).andExpect(jsonPath("$.data", hasSize(2)));
        send(get(BASE).param("status", "inbox"), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].itemType").value("idea"));
        send(get(BASE).param("itemType", "note").param("status", "all"), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("archived"));
        send(get(BASE).param("limit", "1"), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(1)));
        send(get(BASE), bobToken, null).andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void rejectsUnknownFiltersAndLimits() throws Exception {
        send(get(BASE).param("status", "deleted"), aliceToken, null)
                .andExpect(status().isBadRequest());
        send(get(BASE).param("limit", "0"), aliceToken, null).andExpect(status().isBadRequest());
        send(get(BASE).param("limit", "501"), aliceToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void fullTextSearchIsScopedToTheCaller() throws Exception {
        createItem(aliceToken, item("Learn about PostgreSQL indexes", "dev"));
        createItem(aliceToken, item("Buy groceries", "task"));
        createItem(bobToken, item("PostgreSQL vacuum notes", "dev"));

        send(get(BASE).param("q", "postgresql"), aliceToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content").value("Learn about PostgreSQL indexes"));
        send(get(BASE).param("q", "postgresql").param("itemType", "task"), aliceToken, null)
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void anotherUsersItemIsNotFound() throws Exception {
        String bobsItem = createItem(bobToken, item("private", "note"));

        send(get(BASE + "/" + bobsItem), aliceToken, null).andExpect(status().isNotFound());
        send(patch(BASE + "/" + bobsItem), aliceToken, Map.of("pinned", true))
                .andExpect(status().isNotFound());
        send(delete(BASE + "/" + bobsItem), aliceToken, null).andExpect(status().isNotFound());

        send(get(BASE + "/" + bobsItem), bobToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(false));
    }

    @Test
    void patchLeavesOmittedFieldsAndClearsExplicitNulls() throws Exception {
        Map<String, Object> body = item("content", "note");
        body.put("title", "Keep me?");
        body.put("goalId", UUID.randomUUID().toString());
        String id = createItem(aliceToken, body);

        send(patch(BASE + "/" + id), aliceToken, Map.of("pinned", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.title").value("Keep me?"))
                .andExpect(jsonPath("$.data.goalId").isNotEmpty());

        Map<String, Object> clear = new HashMap<>();
        clear.put("title", null);
        clear.put("goalId", null);
        send(patch(BASE + "/" + id), aliceToken, clear)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(nullValue()))
                .andExpect(jsonPath("$.data.goalId").value(nullValue()))
                .andExpect(jsonPath("$.data.content").value("content"));

        String noteId = UUID.randomUUID().toString();
        send(
                        patch(BASE + "/" + id),
                        aliceToken,
                        Map.of(
                                "status", "processed",
                                "convertedToType", "note",
                                "convertedToId", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("processed"))
                .andExpect(jsonPath("$.data.convertedToType").value("note"))
                .andExpect(jsonPath("$.data.convertedToId").value(noteId));
    }

    @Test
    void patchRejectsInvalidValues() throws Exception {
        String id = createItem(aliceToken, item("content", "note"));

        Map<String, Object> nullContent = new HashMap<>();
        nullContent.put("content", null);
        send(patch(BASE + "/" + id), aliceToken, nullContent).andExpect(status().isBadRequest());

        Map<String, Object> nullStatus = new HashMap<>();
        nullStatus.put("status", null);
        send(patch(BASE + "/" + id), aliceToken, nullStatus).andExpect(status().isBadRequest());

        send(patch(BASE + "/" + id), aliceToken, Map.of("status", "deleted"))
                .andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, Map.of("convertedToType", "goal"))
                .andExpect(status().isBadRequest());
        send(patch(BASE + "/" + id), aliceToken, Map.of("content", "c".repeat(4001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTheItem() throws Exception {
        String id = createItem(aliceToken, item("temporary", "note"));

        send(delete(BASE + "/" + id), aliceToken, null).andExpect(status().isOk());
        send(get(BASE + "/" + id), aliceToken, null).andExpect(status().isNotFound());
    }
}
