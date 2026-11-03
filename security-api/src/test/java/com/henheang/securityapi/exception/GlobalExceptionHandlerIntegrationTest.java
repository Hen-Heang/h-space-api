package com.henheang.securityapi.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Client mistakes answer 4xx instead of 500, and no response - including a real 500 - carries
 * exception detail (SQL, constraint names, conflicting values). Exceptions come from the test-only
 * ExceptionProbeController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    private static final String PROBE = "/api/public/test-probe";

    @Autowired private MockMvc mockMvc;

    private ResultActions probe(String path) throws Exception {
        ResultActions result =
                mockMvc.perform(
                        get(PROBE + path)
                                .with(
                                        request -> {
                                            request.setRemoteAddr("10.11.98.1");
                                            return request;
                                        }));
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain(ExceptionProbeController.CANARY)
                .doesNotContain("SQL")
                .doesNotContain("Exception");
        return result;
    }

    @Test
    void aMalformedPathValueIs400NotA500() throws Exception {
        probe("/uuid/not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(40000))
                .andExpect(jsonPath("$.status.message").value("Invalid value for 'id'."));
        // The value itself isn't echoed.
        assertThat(
                        mockMvc.perform(get(PROBE + "/uuid/%3Cscript%3E"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .doesNotContain("script");
    }

    @Test
    void aMalformedOrMissingQueryParameterIs400() throws Exception {
        probe("/param?count=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Invalid value for 'count'."));
        probe("/param")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message").value("Missing required parameter 'count'."));
    }

    @Test
    void aConstraintViolationIs400WithAFieldMap() throws Exception {
        probe("/constraint")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value("Name is required"))
                .andExpect(jsonPath("$.data.name").value("Name is required"));
    }

    @Test
    void uniqueAndForeignKeyViolationsAre409WithoutDatabaseDetail() throws Exception {
        probe("/unique")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status.code").value(40900))
                .andExpect(
                        jsonPath("$.status.message")
                                .value("The request conflicts with existing data."));
        probe("/foreign-key").andExpect(status().isConflict());
    }

    @Test
    void otherIntegrityViolationsAre400WithoutDatabaseDetail() throws Exception {
        probe("/check")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status.message").value("The request contains invalid data."));
    }

    @Test
    void anUnexpectedErrorIs500WithOnlyTheGenericMessage() throws Exception {
        probe("/boom")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status.code").value(5000))
                .andExpect(jsonPath("$.status.message").value("System error"))
                .andExpect(jsonPath("$.data").value("System error"));
    }
}
