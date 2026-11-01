package com.henheang.securityapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henheang.apicore.components.common.api.StatusCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Production turns the API docs off (application-prod.yml). The prod profile can't boot in tests
 * (it has no datasource defaults), so this checks the two halves separately: the prod file disables
 * springdoc by default, and with springdoc disabled nothing under the docs paths is served.
 */
@SpringBootTest(
        properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
class OpenApiDocsDisabledIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void prodProfileDisablesDocsUnlessExplicitlyEnabled() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"))
                .forEach(sources::addLast);
        // Deliberately no environment variables, so API_DOCS_ENABLED falls back.
        PropertySourcesPropertyResolver prod = new PropertySourcesPropertyResolver(sources);

        assertThat(prod.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
    }

    @Test
    void openApiSpecIsNotServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.code").value(StatusCode.NOT_FOUND.getCode()));
    }

    @Test
    void swaggerUiIsNotServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void apiDocsShortcutIsGone() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isNotFound());
    }
}
