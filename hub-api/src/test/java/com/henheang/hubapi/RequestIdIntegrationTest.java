package com.henheang.hubapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henheang.apicore.components.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// hub-api gets RequestIdConfig by scanning com.henheang.apicore (and security-api's @Import);
// the filter must still be registered once and run ahead of the security chain.
@SpringBootTest
@AutoConfigureMockMvc
class RequestIdIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymousVaultCall_isRejected_withRequestIdEchoed() throws Exception {
        mockMvc.perform(get("/api/hub/vault/items").header(RequestIdFilter.HEADER, "hub-req-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "hub-req-1"));
    }
}
