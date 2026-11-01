package com.henheang.securityapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Docker/Compose healthcheck calls /actuator/health without a token, so it must stay public and
 * report UP against the real test database - while every other actuator endpoint stays unexposed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void readinessProbeIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeIsPublicAndMinimal() throws Exception {
        // The image's HEALTHCHECK target: it doesn't query the database, so
        // checks every few seconds don't keep a scale-to-zero DB awake.
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        // Not exposed at all; the security chain answers 401 for the
        // anonymous caller before the (absent) handler would 404.
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
