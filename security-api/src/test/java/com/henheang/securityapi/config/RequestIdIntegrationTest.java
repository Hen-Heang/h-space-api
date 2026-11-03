package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henheang.apicore.components.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * security-api doesn't scan api-core, so this checks RequestIdConfig is imported and that the
 * filter runs ahead of Spring Security (a 401 written by the security chain still carries the id).
 */
@SpringBootTest(properties = "app.cors.allowed-origins=https://app.example.test")
@AutoConfigureMockMvc
class RequestIdIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void publicEndpoint_getsGeneratedRequestId() throws Exception {
        String id =
                mockMvc.perform(get("/actuator/health/liveness"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(RequestIdFilter.HEADER);

        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void unauthenticatedRequest_rejectedBySecurity_stillCarriesClientId() throws Exception {
        mockMvc.perform(get("/actuator/env").header(RequestIdFilter.HEADER, "web-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "web-123"));
    }

    @Test
    void unsafeClientId_isReplaced() throws Exception {
        String id =
                mockMvc.perform(
                                get("/actuator/health/liveness")
                                        .header(RequestIdFilter.HEADER, "bad id\r\nX-Evil: 1"))
                        .andReturn()
                        .getResponse()
                        .getHeader(RequestIdFilter.HEADER);

        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void corsPreflight_allowsRequestIdHeader() throws Exception {
        mockMvc.perform(
                        options("/api/auth/login")
                                .header(HttpHeaders.ORIGIN, "https://app.example.test")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type, x-request-id"))
                .andExpect(status().isOk());
    }

    @Test
    void corsResponse_exposesRequestIdHeader() throws Exception {
        String exposed =
                mockMvc.perform(
                                get("/actuator/health/liveness")
                                        .header(HttpHeaders.ORIGIN, "https://app.example.test"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);

        assertThat(exposed).contains(RequestIdFilter.HEADER);
    }
}
