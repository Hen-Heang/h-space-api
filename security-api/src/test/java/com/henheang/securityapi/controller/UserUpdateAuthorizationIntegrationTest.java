package com.henheang.securityapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.EmailService;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATCH /api/users/{id} on your own account must not let you change your email or its verified
 * flag: the owner-admin rule grants ROLE_ADMIN to whoever holds the owner's verified address, so
 * that would be a self-service route to admin. Each request uses a distinct fake client IP so the 5
 * req/min rate-limit buckets never interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
        properties = {"app.owner-email=" + UserUpdateAuthorizationIntegrationTest.OWNER})
class UserUpdateAuthorizationIntegrationTest {

    static final String OWNER = "owner-user-update-test@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean private EmailService emailService;

    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);

    @BeforeEach
    void stubEmailService() {
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);
    }

    private RequestPostProcessor uniqueClientIp() {
        String ip = "10.31.32." + IP_SUFFIX.getAndIncrement();
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MockHttpServletRequestBuilder jsonPost(String uri, Object body) throws Exception {
        return post(uri)
                .with(uniqueClientIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder jsonPatch(String uri, UUID id, Object body, String token)
            throws Exception {
        return patch(uri, id)
                .with(uniqueClientIp())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    // Signs up, verifies the email and returns the stored user.
    private User verifiedUser(String email) throws Exception {
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/signup",
                                Map.of(
                                        "name",
                                        "Jane " + UUID.randomUUID(),
                                        "email",
                                        email,
                                        "password",
                                        PASSWORD,
                                        "username",
                                        "u"
                                                + UUID.randomUUID()
                                                        .toString()
                                                        .replace("-", "")
                                                        .substring(0, 16))))
                .andExpect(status().isOk());
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token =
                emailVerificationTokenRepository.findByUser(user).stream()
                        .findFirst()
                        .orElseThrow();
        mockMvc.perform(jsonPost("/api/auth/verify-email", Map.of("token", token.getToken())))
                .andExpect(status().isOk());
        return userRepository.findByEmail(email).orElseThrow();
    }

    // Signs in with the password and returns the access token. Sign-in is where the owner's
    // account is granted ROLE_ADMIN.
    @SuppressWarnings("unchecked")
    private String login(String email) throws Exception {
        String body =
                mockMvc.perform(
                                jsonPost(
                                        "/api/auth/login",
                                        Map.of("identifier", email, "password", PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        // The owner's ROLE_ADMIN is saved as a new UserRole row; the test's single transaction
        // would otherwise keep serving the already-loaded User with its old roles. Real requests
        // each get a fresh persistence context.
        entityManager.flush();
        entityManager.clear();
        Map<String, Object> data =
                (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        return (String) data.get("accessToken");
    }

    @Test
    void user_cannotClaimTheOwnersEmail_andBecomeAdmin() throws Exception {
        String email = uniqueEmail();
        User attacker = verifiedUser(email);
        String token = login(email);

        mockMvc.perform(
                        jsonPatch(
                                "/api/users/{id}",
                                attacker.getId(),
                                Map.of(
                                        "email",
                                        OWNER.toUpperCase(Locale.ROOT),
                                        "email_verified",
                                        true),
                                token))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(attacker.getId()).orElseThrow().getEmail())
                .isEqualTo(email);
        mockMvc.perform(
                        get("/api/auth/user")
                                .with(uniqueClientIp())
                                .header("Authorization", "Bearer " + login(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", not(hasItem("ROLE_ADMIN"))));
    }

    @Test
    void user_cannotSetTheirOwnVerifiedFlag() throws Exception {
        String email = uniqueEmail();
        User user = verifiedUser(email);

        mockMvc.perform(
                        jsonPatch(
                                "/api/users/{id}",
                                user.getId(),
                                Map.of("email_verified", false),
                                login(email)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmailVerified()).isTrue();
    }

    @Test
    void user_canStillUpdateTheirOwnName() throws Exception {
        String email = uniqueEmail();
        User user = verifiedUser(email);
        String newName = "Renamed " + UUID.randomUUID();

        mockMvc.perform(
                        jsonPatch(
                                "/api/users/{id}",
                                user.getId(),
                                // Re-sending your current email unchanged is not a change.
                                Map.of("name", newName, "email", email),
                                login(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName));
    }

    @Test
    void admin_canChangeAUsersEmail_butNotToAnotherAccountsAddressInADifferentCase()
            throws Exception {
        verifiedUser(OWNER);
        String adminToken = login(OWNER);
        User user = verifiedUser(uniqueEmail());
        String newEmail = uniqueEmail();

        mockMvc.perform(
                        jsonPatch(
                                "/api/users/{id}",
                                user.getId(),
                                Map.of("email", newEmail),
                                adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail));

        int status =
                mockMvc.perform(
                                jsonPatch(
                                        "/api/users/{id}",
                                        user.getId(),
                                        Map.of("email", OWNER.toUpperCase(Locale.ROOT)),
                                        adminToken))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail())
                .isEqualTo(newEmail);
    }
}
