package com.henheang.securityapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MalformedRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;

    // A client mistake, not a server error - and the parser's message, which
    // can quote the input, isn't echoed back.
    @Test
    void malformedJson_returns400WithAGenericMessage() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .with(
                                        request -> {
                                            request.setRemoteAddr("10.11.99.1");
                                            return request;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"identifier\": \"canary-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(40000))
                .andExpect(
                        jsonPath("$.status.message")
                                .value("Malformed or unexpected request body."));
    }
}
