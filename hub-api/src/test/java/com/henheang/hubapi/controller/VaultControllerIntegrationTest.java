package com.henheang.hubapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.hubapi.config.VaultCleanupTask;
import com.henheang.hubapi.domain.VaultItem;
import com.henheang.hubapi.repository.VaultItemRepository;
import com.henheang.hubapi.repository.VaultKeysetRepository;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.service.AuditLogService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * H-Vault over HTTP against the real (test-profile) schema from hub-api's Flyway migrations. All
 * ciphertext here is random bytes in the v2 envelope shape - the server never decrypts, so it can't
 * tell the difference, and no real or realistic secret is used anywhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class VaultControllerIntegrationTest {

    private static final String BASE = "/api/hub/vault";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private VaultItemRepository itemRepository;
    @Autowired private VaultKeysetRepository keysetRepository;
    @Autowired private VaultCleanupTask cleanupTask;

    @MockitoBean private AuditLogService auditLogService;

    private User owner;
    private String ownerToken;
    private RequestPostProcessor ip;

    @BeforeEach
    void setUp() {
        owner = newUser(true);
        ownerToken = jwtTokenProvider.generateToken(owner);
        ip = nextIp();
    }

    // A distinct client IP, so the shared RateLimitingFilter buckets (per IP +
    // path) never leak between tests or requests that aren't about limits.
    private static RequestPostProcessor nextIp() {
        int n = IP_SUFFIX.getAndIncrement();
        String address = "10.20." + (n / 250) + "." + (n % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    // --- access ---------------------------------------------------------------

    @Test
    void everyEndpoint_withoutToken_returns401() throws Exception {
        for (MockHttpServletRequestBuilder request : allEndpoints()) {
            mockMvc.perform(request.with(ip)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void everyEndpoint_forAccountWithoutMfa_returns403() throws Exception {
        String token = jwtTokenProvider.generateToken(newUser(false));
        for (MockHttpServletRequestBuilder request : allEndpoints()) {
            mockMvc.perform(request.with(ip).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status.code").value(40301));
        }
    }

    @Test
    void turningMfaOff_closesTheVaultImmediately() throws Exception {
        createKeyset(ownerToken);
        owner.setMfaEnabled(false);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(authed(get(BASE + "/items"), ownerToken)).andExpect(status().isForbidden());
    }

    // --- keyset ---------------------------------------------------------------

    @Test
    void keyset_beforeSetup_returns404() throws Exception {
        mockMvc.perform(authed(get(BASE + "/keyset"), ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status.message").value("Vault isn't set up yet."));
    }

    @Test
    void keyset_createReadAndReplace() throws Exception {
        Map<String, Object> body = keysetBody();
        JsonNode created = data(mockMvc.perform(jsonPut(BASE + "/keyset", body, ownerToken)));
        assertThat(created.get("wrappedKey").asText()).isEqualTo(body.get("wrappedKey"));
        assertThat(created.get("kdfIterations").asInt()).isEqualTo(600_000);

        JsonNode read = data(mockMvc.perform(authed(get(BASE + "/keyset"), ownerToken)));
        assertThat(read.get("kdfSalt").asText()).isEqualTo(body.get("kdfSalt"));

        // Master-password change: new salt and wrap, same version as read.
        Map<String, Object> changed = keysetBody();
        changed.put("version", read.get("version").asLong());
        JsonNode replaced = data(mockMvc.perform(jsonPut(BASE + "/keyset", changed, ownerToken)));
        assertThat(replaced.get("wrappedKey").asText()).isEqualTo(changed.get("wrappedKey"));
        assertThat(replaced.get("version").asLong()).isGreaterThan(read.get("version").asLong());

        verify(auditLogService)
                .log(eq(AuditEventType.VAULT_KEYSET_CREATED), eq(owner.getId()), isNull());
        verify(auditLogService)
                .log(eq(AuditEventType.VAULT_KEYSET_CHANGED), eq(owner.getId()), isNull());
    }

    @Test
    void keyset_replaceWithStaleVersion_returns409() throws Exception {
        long version = createKeyset(ownerToken).get("version").asLong();
        Map<String, Object> first = keysetBody();
        first.put("version", version);
        mockMvc.perform(jsonPut(BASE + "/keyset", first, ownerToken)).andExpect(status().isOk());

        Map<String, Object> stale = keysetBody();
        stale.put("version", version);
        mockMvc.perform(jsonPut(BASE + "/keyset", stale, ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void keyset_replaceWithoutVersion_returns409() throws Exception {
        createKeyset(ownerToken);
        mockMvc.perform(jsonPut(BASE + "/keyset", keysetBody(), ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void keyset_rejectsWeakOrMalformedValues() throws Exception {
        List<Map<String, Object>> bad = new ArrayList<>();
        bad.add(with(keysetBody(), "kdfIterations", 100_000));
        bad.add(with(keysetBody(), "kdf", "MD5"));
        bad.add(with(keysetBody(), "kdfSalt", "short"));
        bad.add(with(keysetBody(), "wrappedKey", "my-plain-master-password"));
        bad.add(with(keysetBody(), "wrappedKey", "v1." + b64(16) + "." + b64(12) + "." + b64(48)));
        bad.add(with(keysetBody(), "recoveryWrappedKey", null));
        for (Map<String, Object> body : bad) {
            // Six writes to a rate-limited path: give each its own client.
            ip = nextIp();
            mockMvc.perform(jsonPut(BASE + "/keyset", body, ownerToken))
                    .andExpect(status().isBadRequest());
        }
        assertThat(keysetRepository.existsById(owner.getId())).isFalse();
    }

    // --- items ----------------------------------------------------------------

    @Test
    void items_requireAKeysetFirst() throws Exception {
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void items_createListUpdateDelete() throws Exception {
        createKeyset(ownerToken);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<String, Object> firstBody = itemBody(first);
        JsonNode created = data(mockMvc.perform(jsonPost(BASE + "/items", firstBody, ownerToken)));
        assertThat(created.get("id").asText()).isEqualTo(first.toString());
        assertThat(created.get("payloadCiphertext").asText())
                .isEqualTo(firstBody.get("payloadCiphertext"));
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(second), ownerToken))
                .andExpect(status().isOk());

        // Newest change first: editing `first` moves it to the top.
        Map<String, Object> update = new HashMap<>();
        update.put("payloadCiphertext", envelope(80));
        update.put("version", created.get("version").asLong());
        JsonNode updated =
                data(mockMvc.perform(jsonPut(BASE + "/items/" + first, update, ownerToken)));
        assertThat(updated.get("payloadCiphertext").asText())
                .isEqualTo(update.get("payloadCiphertext"));
        assertThat(updated.get("version").asLong()).isGreaterThan(created.get("version").asLong());

        JsonNode list = data(mockMvc.perform(authed(get(BASE + "/items"), ownerToken)));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asText()).isEqualTo(first.toString());

        mockMvc.perform(authed(delete(BASE + "/items/" + second), ownerToken))
                .andExpect(status().isOk());
        assertThat(data(mockMvc.perform(authed(get(BASE + "/items"), ownerToken)))).hasSize(1);

        verify(auditLogService)
                .log(AuditEventType.VAULT_ITEM_CREATED, owner.getId(), null, "item=" + first);
        verify(auditLogService)
                .log(AuditEventType.VAULT_ITEM_UPDATED, owner.getId(), null, "item=" + first);
        verify(auditLogService)
                .log(AuditEventType.VAULT_ITEM_DELETED, owner.getId(), null, "item=" + second);
    }

    @Test
    void items_updateWithStaleVersion_returns409() throws Exception {
        createKeyset(ownerToken);
        UUID id = UUID.randomUUID();
        long version =
                data(mockMvc.perform(jsonPost(BASE + "/items", itemBody(id), ownerToken)))
                        .get("version")
                        .asLong();
        Map<String, Object> update = new HashMap<>();
        update.put("payloadCiphertext", envelope(64));
        update.put("version", version);
        mockMvc.perform(jsonPut(BASE + "/items/" + id, update, ownerToken))
                .andExpect(status().isOk());

        // A second device still holding the old version.
        update.put("payloadCiphertext", envelope(64));
        mockMvc.perform(jsonPut(BASE + "/items/" + id, update, ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void items_ofAnotherUser_areInvisibleAndUntouchable() throws Exception {
        createKeyset(ownerToken);
        UUID ownerItem = UUID.randomUUID();
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(ownerItem), ownerToken))
                .andExpect(status().isOk());

        String otherToken = jwtTokenProvider.generateToken(newUser(true));
        createKeyset(otherToken);

        assertThat(data(mockMvc.perform(authed(get(BASE + "/items"), otherToken)))).isEmpty();
        Map<String, Object> update = new HashMap<>();
        update.put("payloadCiphertext", envelope(64));
        update.put("version", 0);
        mockMvc.perform(jsonPut(BASE + "/items/" + ownerItem, update, otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(delete(BASE + "/items/" + ownerItem), otherToken))
                .andExpect(status().isNotFound());
        // Re-using the id answers like any duplicate, without saying whose it is.
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(ownerItem), otherToken))
                .andExpect(status().isConflict());

        VaultItem stored = itemRepository.findById(ownerItem).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(owner.getId());
    }

    @Test
    void items_rejectAnythingThatIsntAV2Envelope() throws Exception {
        createKeyset(ownerToken);
        List<String> bad =
                List.of(
                        "correct horse battery staple",
                        "v1." + b64(16) + "." + b64(12) + "." + b64(40),
                        "v2." + b64(8) + "." + b64(40),
                        "v2." + b64(12) + ".!!notbase64!!");
        for (String ciphertext : bad) {
            Map<String, Object> body = itemBody(UUID.randomUUID());
            body.put("payloadCiphertext", ciphertext);
            mockMvc.perform(jsonPost(BASE + "/items", body, ownerToken))
                    .andExpect(status().isBadRequest());
        }
        Map<String, Object> tooLarge = itemBody(UUID.randomUUID());
        tooLarge.put("payloadCiphertext", envelope(13_000));
        mockMvc.perform(jsonPost(BASE + "/items", tooLarge, ownerToken))
                .andExpect(status().isBadRequest());
        assertThat(itemRepository.countByUserId(owner.getId())).isZero();
    }

    @Test
    void items_rejectUnknownFieldsWithoutEchoingThem() throws Exception {
        createKeyset(ownerToken);
        Map<String, Object> body = itemBody(UUID.randomUUID());
        body.put("password", "plain-text-leak-canary");
        MvcResult result =
                mockMvc.perform(jsonPost(BASE + "/items", body, ownerToken))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("leak-canary");
        assertThat(itemRepository.countByUserId(owner.getId())).isZero();
    }

    @Test
    void items_malformedJson_returns400() throws Exception {
        mockMvc.perform(
                        post(BASE + "/items")
                                .with(ip)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"id\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void items_areCappedPerUser() throws Exception {
        createKeyset(ownerToken);
        List<VaultItem> items = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < 1000; i++) {
            VaultItem item = new VaultItem();
            item.setId(UUID.randomUUID());
            item.setUserId(owner.getId());
            item.setPayloadCiphertext(envelope(32));
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            items.add(item);
        }
        itemRepository.saveAllAndFlush(items);

        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void nothingSentToTheVaultIsLogged(CapturedOutput output) throws Exception {
        createKeyset(ownerToken);
        Map<String, Object> body = itemBody(UUID.randomUUID());
        mockMvc.perform(jsonPost(BASE + "/items", body, ownerToken)).andExpect(status().isOk());
        Map<String, Object> bad = itemBody(UUID.randomUUID());
        bad.put("payloadCiphertext", "v2.notvalid-canary");
        mockMvc.perform(jsonPost(BASE + "/items", bad, ownerToken))
                .andExpect(status().isBadRequest());

        String ciphertextBody = ((String) body.get("payloadCiphertext")).split("\\.")[2];
        assertThat(output.getAll())
                .doesNotContain(ciphertextBody)
                .doesNotContain("notvalid-canary");
    }

    // --- reset ----------------------------------------------------------------

    @Test
    void reset_requiresConfirmation() throws Exception {
        createKeyset(ownerToken);
        mockMvc.perform(jsonPost(BASE + "/reset", Map.of("confirm", "yes"), ownerToken))
                .andExpect(status().isBadRequest());
        assertThat(keysetRepository.existsById(owner.getId())).isTrue();
    }

    @Test
    void reset_deletesKeysetAndEveryItem_andAllowsANewVault() throws Exception {
        createKeyset(ownerToken);
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(jsonPost(BASE + "/reset", Map.of("confirm", "RESET"), ownerToken))
                .andExpect(status().isOk());

        assertThat(itemRepository.countByUserId(owner.getId())).isZero();
        assertThat(keysetRepository.existsById(owner.getId())).isFalse();
        verify(auditLogService).log(AuditEventType.VAULT_RESET, owner.getId(), null, "items=2");
        mockMvc.perform(jsonPut(BASE + "/keyset", keysetBody(), ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void reset_isRateLimited_butReadingTheKeysetIsNot() throws Exception {
        createKeyset(ownerToken);
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(authed(get(BASE + "/keyset"), ownerToken)).andExpect(status().isOk());
        }
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(jsonPost(BASE + "/reset", Map.of("confirm", "RESET"), ownerToken))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(jsonPost(BASE + "/reset", Map.of("confirm", "RESET"), ownerToken))
                .andExpect(status().isTooManyRequests());
    }

    // --- account deletion -----------------------------------------------------

    @Test
    void cleanup_purgesTheVaultOfDeletedAccounts_only() throws Exception {
        createKeyset(ownerToken);
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), ownerToken))
                .andExpect(status().isOk());
        User other = newUser(true);
        String otherToken = jwtTokenProvider.generateToken(other);
        createKeyset(otherToken);
        mockMvc.perform(jsonPost(BASE + "/items", itemBody(UUID.randomUUID()), otherToken))
                .andExpect(status().isOk());

        userRepository.delete(userRepository.findById(owner.getId()).orElseThrow());
        userRepository.flush();
        cleanupTask.purgeDeletedAccounts();

        assertThat(itemRepository.countByUserId(owner.getId())).isZero();
        assertThat(keysetRepository.existsById(owner.getId())).isFalse();
        assertThat(itemRepository.countByUserId(other.getId())).isEqualTo(1);
        assertThat(keysetRepository.existsById(other.getId())).isTrue();
    }

    // --- helpers --------------------------------------------------------------

    private User newUser(boolean mfaEnabled) {
        User user = new User();
        user.setEmail("vault-" + UUID.randomUUID() + "@example.com");
        user.setName("Vault Test");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user.setMfaEnabled(mfaEnabled);
        return userRepository.saveAndFlush(user);
    }

    private List<MockHttpServletRequestBuilder> allEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> update = new HashMap<>();
        update.put("payloadCiphertext", envelope(32));
        update.put("version", 0);
        return List.of(
                get(BASE + "/keyset"),
                json(put(BASE + "/keyset"), keysetBody()),
                json(post(BASE + "/reset"), Map.of("confirm", "RESET")),
                get(BASE + "/items"),
                json(post(BASE + "/items"), itemBody(id)),
                json(put(BASE + "/items/" + id), update),
                delete(BASE + "/items/" + id));
    }

    private JsonNode createKeyset(String token) throws Exception {
        return data(mockMvc.perform(jsonPut(BASE + "/keyset", keysetBody(), token)));
    }

    private static Map<String, Object> keysetBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("kdf", "PBKDF2-SHA256");
        body.put("kdfIterations", 600_000);
        body.put("kdfSalt", b64(16));
        body.put("wrappedKey", wrappedKey());
        body.put("recoveryWrappedKey", wrappedKey());
        return body;
    }

    private static Map<String, Object> itemBody(UUID id) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id.toString());
        body.put("payloadCiphertext", envelope(64));
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        body.put(key, value);
        return body;
    }

    /** A random v2 envelope: 12-byte IV, then {@code bytes} of "ciphertext + tag". */
    private static String envelope(int bytes) {
        return "v2." + b64(12) + "." + b64(bytes);
    }

    private static String wrappedKey() {
        return envelope(48);
    }

    private static String b64(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder authed(
            MockHttpServletRequestBuilder request, String token) {
        return request.with(ip).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder jsonPost(String uri, Object body, String token)
            throws Exception {
        return authed(json(post(uri), body), token);
    }

    private MockHttpServletRequestBuilder jsonPut(String uri, Object body, String token)
            throws Exception {
        return authed(json(put(uri), body), token);
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        String content =
                result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("data");
    }
}
