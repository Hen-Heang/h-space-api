package com.henheang.securityapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger UI is how the API is tried out by hand, so the OpenAPI spec must actually generate (a
 * springdoc/Spring Boot mismatch only fails at request time) and stay reachable without a token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void publicGroupSpecIsGeneratedWithoutAToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("H-Space API"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/mfa/verify']").exists());
    }

    @Test
    void authGroupSpecOnlyCoversAuthEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs/auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/users']").doesNotExist());
    }

    @Test
    void swaggerConfigListsBothGroups() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[?(@.name == 'public')]").exists())
                .andExpect(jsonPath("$.urls[?(@.name == 'auth')]").exists());
    }

    @Test
    void swaggerUiIsServedWithoutAToken() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
    }

    @Test
    void apiDocsShortcutRedirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
