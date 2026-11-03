package com.henheang.securityapi.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henheang.apicore.components.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
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

    @Test
    void queryAndTransactionTimeoutsAre503TryAgain() throws Exception {
        for (String path : new String[] {"/query-timeout", "/transaction-timeout"}) {
            probe(path)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status.code").value(50301))
                    .andExpect(
                            jsonPath("$.status.message")
                                    .value("The service is busy right now. Please try again."));
        }
    }

    @Test
    void errorBodiesCarryTheRequestId_matchingTheHeader() throws Exception {
        String body =
                mockMvc.perform(get(PROBE + "/boom").header(RequestIdFilter.HEADER, "err-ref-42"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(header().string(RequestIdFilter.HEADER, "err-ref-42"))
                        .andExpect(jsonPath("$.status.requestId").value("err-ref-42"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).contains("err-ref-42");
    }

    @Test
    void errorBodyCarriesTheGeneratedId_whenTheClientSentNone() throws Exception {
        MockHttpServletResponse response =
                mockMvc.perform(get(PROBE + "/check")).andReturn().getResponse();
        String header = response.getHeader(RequestIdFilter.HEADER);

        assertThat(header).isNotBlank();
        assertThat(JsonPath.<String>read(response.getContentAsString(), "$.status.requestId"))
                .isEqualTo(header);
    }

    @Test
    void successEnvelopesAreUnchanged_noRequestIdField() throws Exception {
        mockMvc.perform(get(PROBE + "/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.status.requestId").doesNotExist());
    }
}
