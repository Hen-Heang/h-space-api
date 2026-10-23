package com.henheang.securityapi.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.EmailService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the full signup/login/refresh/logout HTTP flow against a real (test-profile) database.
 * Each test uses a distinct fake client IP so the shared RateLimitingFilter bucket (5 req/min per
 * IP+path) never interferes between test methods.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;

    // Real EmailService would try a live SMTP connection - mocked so
    // signup/verification flows run without network access, while the
    // verification token itself is still persisted for real by the service.
    @MockitoBean private EmailService emailService;

    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);

    @BeforeEach
    void stubEmailService() {
        when(emailService.sendPasswordResetEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendAccountLockedEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendAccountUnlockedEmail(any(), any())).thenReturn(true);
    }

    private RequestPostProcessor uniqueClientIp() {
        String ip = "10.11.12." + IP_SUFFIX.getAndIncrement();
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private MockHttpServletRequestBuilder jsonPost(String uri, Object body, RequestPostProcessor ip)
            throws Exception {
        return post(uri)
                .with(ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private void verifyEmailFor(String email, RequestPostProcessor ip) throws Exception {
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token =
                emailVerificationTokenRepository.findByUser(user).stream()
                        .findFirst()
                        .orElseThrow();
        mockMvc.perform(jsonPost("/api/auth/verify-email", Map.of("token", token.getToken()), ip))
                .andExpect(status().isOk());
    }

    @Test
    void signup_createsUserAndReturnsTokens() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        Map<String, String> signUpRequest =
                Map.of(
                        "name", "Jane Doe",
                        "email", uniqueEmail(),
                        "password", "Password123!");

        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void signup_withDuplicateEmail_returns400() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");

        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip)).andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_withInvalidPayload_returns400() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        Map<String, String> invalidRequest =
                Map.of("name", "", "email", "not-an-email", "password", "short");

        mockMvc.perform(jsonPost("/api/auth/signup", invalidRequest, ip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withCorrectCredentials_returnsTokens() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip)).andExpect(status().isOk());
        verifyEmailFor(email, ip);

        Map<String, String> loginRequest = Map.of("email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/login", loginRequest, ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()));
    }

    @Test
    void login_beforeEmailIsVerified_returns401() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip)).andExpect(status().isOk());

        Map<String, String> loginRequest = Map.of("email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/login", loginRequest, ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip)).andExpect(status().isOk());
        verifyEmailFor(email, ip);

        Map<String, String> loginRequest = Map.of("email", email, "password", "wrong-password");
        mockMvc.perform(jsonPost("/api/auth/login", loginRequest, ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesRefreshTokenAndRejectsThePreviousOne() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        String signUpBody =
                mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String originalRefreshToken = readJsonField(signUpBody, "refreshToken");

        Map<String, String> refreshRequest = Map.of("refreshToken", originalRefreshToken);
        String refreshBody =
                mockMvc.perform(jsonPost("/api/auth/refresh", refreshRequest, ip))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String rotatedRefreshToken = readJsonField(refreshBody, "refreshToken");

        org.assertj.core.api.Assertions.assertThat(rotatedRefreshToken)
                .isNotEqualTo(originalRefreshToken);

        // The token just presented was revoked by rotation, so reusing it must fail.
        mockMvc.perform(jsonPost("/api/auth/refresh", refreshRequest, ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshTokenAndBlacklistsAccessToken() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        String signUpBody =
                mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String accessToken = readJsonField(signUpBody, "accessToken");
        String refreshToken = readJsonField(signUpBody, "refreshToken");

        // Access token works before logout.
        mockMvc.perform(
                        get("/api/auth/user")
                                .with(ip)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Map<String, String> logoutRequest = Map.of("refreshToken", refreshToken);
        mockMvc.perform(
                        jsonPost("/api/auth/logout", logoutRequest, ip)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Access token is now blacklisted.
        mockMvc.perform(
                        get("/api/auth/user")
                                .with(ip)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // Refresh token is now revoked.
        mockMvc.perform(jsonPost("/api/auth/refresh", logoutRequest, ip))
                .andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("unchecked")
    private String readJsonField(String responseBody, String field) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get(field);
    }
}
