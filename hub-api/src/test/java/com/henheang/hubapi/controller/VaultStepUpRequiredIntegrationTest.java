package com.henheang.hubapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * With hub.vault.step-up-required on, reset and keyset replacement need a fresh second-factor code
 * on top of the session (vault-design.md §8). Correct codes here are real backup codes from
 * MfaBackupCodeService; the authenticator-code rules are covered in security-api's
 * MfaStepUpServiceIntegrationTest.
 */
@SpringBootTest(properties = "hub.vault.step-up-required=true")
@AutoConfigureMockMvc
class VaultStepUpRequiredIntegrationTest extends VaultStepUpTestSupport {

    @Test
    void reset_withoutACode_isRefused_andTheVaultIsKept() throws Exception {
        reset(null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(40302));
        assertThat(vaultExists()).isTrue();
    }

    // The vault's transaction rolls back on the 403; the failure count, kept by
    // MfaStepUpService in its own transaction, must not roll back with it.
    @Test
    void reset_withAWrongCode_isRefused_andTheFailureStillCounts() throws Exception {
        reset("ZZZZZ-ZZZZZ")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status.code").value(40303));
        assertThat(vaultExists()).isTrue();
        assertThat(failedAttempts()).isEqualTo(1);
    }

    @Test
    void reset_withABackupCode_deletesTheVault_andTheCodeCantBeReused() throws Exception {
        String code = codes.get(0);

        reset(code).andExpect(status().isOk());
        assertThat(vaultExists()).isFalse();

        reset(code).andExpect(jsonPath("$.status.code").value(40303));
    }

    @Test
    void creatingTheFirstKeyset_needsNoCode_butReplacingItDoes() throws Exception {
        // Set-up already created the first keyset without a code.
        assertThat(vaultExists()).isTrue();

        replaceKeyset(null).andExpect(jsonPath("$.status.code").value(40302));
        replaceKeyset("ZZZZZ-ZZZZZ").andExpect(jsonPath("$.status.code").value(40303));
        replaceKeyset(codes.get(1)).andExpect(status().isOk());
    }

    @Test
    void fiveWrongCodesLock_andThenEvenAValidCodeIsRefused() throws Exception {
        for (int i = 0; i < 4; i++) {
            reset("ZZZZZ-ZZZZZ").andExpect(jsonPath("$.status.code").value(40303));
        }
        reset("ZZZZZ-ZZZZZ").andExpect(jsonPath("$.status.code").value(40304));

        reset(codes.get(2)).andExpect(jsonPath("$.status.code").value(40304));
        assertThat(vaultExists()).isTrue();
    }

    @Test
    void aMalformedCode_isA400_withoutEchoingIt() throws Exception {
        String body =
                reset("not-a-code-<script>")
                        .andExpect(status().isBadRequest())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain("<script>");
        assertThat(failedAttempts()).isZero();
    }
}
