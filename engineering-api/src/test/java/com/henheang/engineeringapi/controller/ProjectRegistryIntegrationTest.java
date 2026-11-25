package com.henheang.engineeringapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.henheang.engineeringapi.security.OwnerAuthenticationConverter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The registry against the real Postgres schema (Flyway V2). Callers are two synthetic owners, as
 * the security chain would pass them (the chain itself is EngineeringSecurityIntegrationTest's
 * job): each gets the owner authority and its own {@code sub}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectRegistryIntegrationTest {

    // Both in application-test.yml's owner-ids.
    private static final UUID ALICE = UUID.fromString("0b0e5c2a-7d3c-4c8e-9a51-2f6d8e4b1a01");
    private static final UUID BOB = UUID.fromString("5c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f");
    private static final String PROJECTS = "/api/engineering/projects";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void emptyRegistry() {
        jdbc.update("DELETE FROM engineering_api_test.projects");
    }

    @Test
    void create_returns201_andTheStoredProject() throws Exception {
        send(post(PROJECTS), ALICE, project("H-Space Web", "h-space-web"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.slug").value("h-space-web"))
                .andExpect(jsonPath("$.data.githubRepo").value("Hen-Heang/h-space"))
                .andExpect(jsonPath("$.data.computeBudgetCuHours").value(20.0))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void list_showsOnlyTheCallersProjects_sortedByName() throws Exception {
        create(ALICE, project("zeta", "zeta"));
        create(ALICE, project("Alpha", "alpha"));
        create(BOB, project("Bob's", "bobs"));

        send(get(PROJECTS), ALICE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].slug").value("alpha"))
                .andExpect(jsonPath("$.data[1].slug").value("zeta"));
    }

    @Test
    void anotherOwnersProject_is404_forReadUpdateAndDelete_andStaysIntact() throws Exception {
        JsonNode alices = create(ALICE, project("Private", "private"));
        String path = PROJECTS + "/" + alices.get("id").asText();

        send(get(path), BOB, null).andExpect(status().isNotFound());
        send(put(path), BOB, project("Taken over", "private").put("version", 0))
                .andExpect(status().isNotFound());
        send(delete(path), BOB, null).andExpect(status().isNotFound());

        send(get(path), ALICE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Private"));
    }

    @Test
    void slug_isUniquePerOwner_notGlobally() throws Exception {
        create(ALICE, project("One", "shared-slug"));

        send(post(PROJECTS), ALICE, project("Two", "shared-slug"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status.code").value(40900));
        send(post(PROJECTS), BOB, project("Bob's", "shared-slug")).andExpect(status().isCreated());
    }

    @Test
    void replace_needsTheVersionItWasReadAt() throws Exception {
        JsonNode created = create(ALICE, project("Before", "app"));
        String path = PROJECTS + "/" + created.get("id").asText();

        send(put(path), ALICE, project("After", "app").put("version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("After"))
                .andExpect(jsonPath("$.data.version").value(1));

        // A second device still holding version 0 can't overwrite it.
        send(put(path), ALICE, project("Stale", "app").put("version", 0))
                .andExpect(status().isConflict());
        // No version at all is refused rather than overwriting blindly.
        send(put(path), ALICE, project("Blind", "app")).andExpect(status().isBadRequest());
        send(get(path), ALICE, null).andExpect(jsonPath("$.data.name").value("After"));
    }

    @Test
    void replace_cantTakeASlugTheOwnerAlreadyUses() throws Exception {
        create(ALICE, project("One", "one"));
        JsonNode two = create(ALICE, project("Two", "two"));

        send(
                        put(PROJECTS + "/" + two.get("id").asText()),
                        ALICE,
                        project("Two", "one").put("version", 0))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_removesTheProject() throws Exception {
        String path = PROJECTS + "/" + create(ALICE, project("Gone", "gone")).get("id").asText();

        send(delete(path), ALICE, null).andExpect(status().isOk());
        send(get(path), ALICE, null).andExpect(status().isNotFound());
    }

    @Test
    void invalidInput_is400_andNothingIsStored() throws Exception {
        ObjectNode[] invalid = {
            project("Bad slug", "Not A Slug"),
            project("Plain http", "plain-http").put("productionUrl", "http://example.com"),
            project("Credentials", "creds")
                    .put("productionUrl", "https://user:secret@example.com/"),
            project("No dot", "no-dot").put("productionUrl", "https://localhost/"),
            project("Bad repo", "bad-repo").put("githubRepo", "not a repo"),
            project("Bad hosting", "bad-hosting").put("hosting", "heroku"),
            project("Cost alone", "cost-alone").putNull("costCurrency"),
            project("Version on create", "version-on-create").put("version", 0),
            // Unknown fields are refused, so an owner id can't ride in a body.
            project("Smuggled owner", "smuggled").put("userId", BOB.toString()),
            project("", "blank-name"),
        };
        for (ObjectNode body : invalid) {
            send(post(PROJECTS), ALICE, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status.code").value(40000))
                    .andExpect(jsonPath("$.status.requestId", notNullValue()));
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM engineering_api_test.projects", Long.class))
                .isZero();
    }

    @Test
    void aProjectIdThatIsNotAUuid_is400() throws Exception {
        send(get(PROJECTS + "/not-a-uuid"), ALICE, null).andExpect(status().isBadRequest());
    }

    private JsonNode create(UUID owner, ObjectNode body) throws Exception {
        String response =
                send(post(PROJECTS), owner, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).get("data");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID owner, ObjectNode body)
            throws Exception {
        request.with(owner(owner));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private static RequestPostProcessor owner(UUID owner) {
        return jwt().jwt(token -> token.subject(owner.toString()).claim("aal", "aal2"))
                .authorities(new SimpleGrantedAuthority(OwnerAuthenticationConverter.OWNER));
    }

    /** A valid project; tests break one field at a time. Synthetic values only. */
    private ObjectNode project(String name, String slug) {
        ObjectNode body = json.createObjectNode();
        body.put("name", name);
        body.put("slug", slug);
        body.put("description", "Synthetic test project");
        body.put("githubRepo", "Hen-Heang/h-space");
        body.put("productionUrl", "https://example.com/app");
        body.put("hosting", "vercel");
        body.put("neonProjectId", "example-project-123");
        body.put("monthlyCostCents", 500);
        body.put("costCurrency", "USD");
        body.put("computeBudgetCuHours", 20);
        return body;
    }
}
