package com.henheang.hubapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.hubapi.repository.VaultItemRepository;
import com.henheang.hubapi.repository.VaultKeysetRepository;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared set-up for the vault step-up tests. Not @Transactional: MfaStepUpService runs in its own
 * transaction and only sees committed users, so the user and their vault are committed here and
 * hard-deleted afterwards. Synthetic ciphertext only, as in VaultControllerIntegrationTest.
 */
abstract class VaultStepUpTestSupport {

    static final String BASE = "/api/hub/vault";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired VaultItemRepository itemRepository;
    @Autowired VaultKeysetRepository keysetRepository;
    @Autowired MfaBackupCodeService backupCodes;
    @Autowired MfaService mfaService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired TransactionTemplate transactionTemplate;

    // No unlock emails and no audit rows; neither is what's under test.
    @MockitoBean AccountUnlockService accountUnlockService;
    @MockitoBean AuditLogService auditLogService;

    User owner;
    String token;
    List<String> codes;

    @BeforeEach
    void createOwnerWithVault() throws Exception {
        User user = new User();
        user.setEmail("vault-step-up-" + UUID.randomUUID() + "@example.com");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user.setMfaEnabled(true);
        user.setMfaSecret(mfaService.generateSecret());
        owner = userRepository.saveAndFlush(user);
        token = jwtTokenProvider.generateToken(owner);
        codes = new ArrayList<>(backupCodes.regenerate(owner));
        send(put(BASE + "/keyset"), keysetBody()).andExpect(status().isOk());
    }

    @AfterEach
    void deleteOwner() {
        transactionTemplate.executeWithoutResult(
                tx -> {
                    itemRepository.deleteByUserId(owner.getId());
                    keysetRepository.deleteByUserId(owner.getId());
                });
        backupCodes.clear(owner);
        userRepository.deleteById(owner.getId());
    }

    /** A request from a fresh client IP, so /reset and /keyset rate limits never interfere. */
    ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Object body)
            throws Exception {
        String ip =
                "10.30." + (IP_SUFFIX.get() / 250) + "." + (IP_SUFFIX.getAndIncrement() % 250 + 1);
        return mockMvc.perform(
                request.with(
                                r -> {
                                    r.setRemoteAddr(ip);
                                    return r;
                                })
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    ResultActions reset(String mfaCode) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("confirm", "RESET");
        if (mfaCode != null) {
            body.put("mfaCode", mfaCode);
        }
        return send(post(BASE + "/reset"), body);
    }

    ResultActions replaceKeyset(String mfaCode) throws Exception {
        Map<String, Object> body = keysetBody();
        body.put("version", currentKeysetVersion());
        if (mfaCode != null) {
            body.put("mfaCode", mfaCode);
        }
        return send(put(BASE + "/keyset"), body);
    }

    boolean vaultExists() {
        return keysetRepository.existsById(owner.getId());
    }

    int failedAttempts() {
        return userRepository.findById(owner.getId()).orElseThrow().getFailedLoginAttempts();
    }

    private long currentKeysetVersion() {
        return keysetRepository.findById(owner.getId()).orElseThrow().getVersion();
    }

    static Map<String, Object> keysetBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("kdf", "PBKDF2-SHA256");
        body.put("kdfIterations", 600_000);
        body.put("kdfSalt", b64(16));
        body.put("wrappedKey", "v2." + b64(12) + "." + b64(48));
        body.put("recoveryWrappedKey", "v2." + b64(12) + "." + b64(48));
        return body;
    }

    private static String b64(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }
}
