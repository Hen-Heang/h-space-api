package com.henheang.hubapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The default until the web app sends `mfaCode`: no code is still accepted, so the current client
 * keeps working - but a code that is sent is always verified, never ignored.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VaultStepUpOptionalIntegrationTest extends VaultStepUpTestSupport {

    @Test
    void withoutACode_resetAndReplacementStillWork_forTheCurrentClient() throws Exception {
        replaceKeyset(null).andExpect(status().isOk());
        reset(null).andExpect(status().isOk());
        assertThat(vaultExists()).isFalse();
    }

    @Test
    void aCodeThatIsSent_isVerified_evenThoughNoneIsRequired() throws Exception {
        reset("ZZZZZ-ZZZZZ")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(40303));
        assertThat(vaultExists()).isTrue();
        assertThat(failedAttempts()).isEqualTo(1);

        reset(codes.get(0)).andExpect(status().isOk());
        assertThat(vaultExists()).isFalse();
    }
}
