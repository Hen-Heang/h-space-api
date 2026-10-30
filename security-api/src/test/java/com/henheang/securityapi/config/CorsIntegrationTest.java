package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The web app calls this API cross-origin, so its origin must come from app.cors.allowed-origins
 * (CORS_ALLOWED_ORIGINS) - not a list baked into the code.
 */
@SpringBootTest(
        properties =
                "app.cors.allowed-origins=https://app.example.test, https://*.preview.example.test")
@AutoConfigureMockMvc
class CorsIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(
                options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"));
    }

    @Test
    void preflight_fromConfiguredOrigin_isAllowed() throws Exception {
        preflight("https://app.example.test")
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "https://app.example.test"));
    }

    @Test
    void preflight_fromOriginMatchingPattern_isAllowed() throws Exception {
        preflight("https://pr-12.preview.example.test")
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                        "https://pr-12.preview.example.test"));
    }

    @Test
    void preflight_fromOtherOrigin_isRejected() throws Exception {
        preflight("http://localhost:3000")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void parseAllowedOrigins_trimsAndDropsBlanks() {
        assertThat(WebSecurityConfig.parseAllowedOrigins(" https://a.test ,, https://b.test "))
                .containsExactly("https://a.test", "https://b.test");
    }

    @Test
    void parseAllowedOrigins_refusesWildcardAndEmpty() {
        assertThatThrownBy(() -> WebSecurityConfig.parseAllowedOrigins("https://a.test,*"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> WebSecurityConfig.parseAllowedOrigins(" , "))
                .isInstanceOf(IllegalStateException.class);
    }
}
