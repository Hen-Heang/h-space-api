package com.henheang.securityapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuditLog;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.AuditLogRepository;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.validation.UsernameRules;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
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
    @Autowired private AuditLogRepository auditLogRepository;

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
        when(emailService.sendLoginCodeEmail(any(), any(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            lastLoginCode.set(invocation.getArgument(1));
                            return true;
                        });
    }

    // The code the mocked EmailService was last asked to send.
    private final AtomicReference<String> lastLoginCode = new AtomicReference<>();

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

    private String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
                        "name",
                        "Jane Doe",
                        "email",
                        uniqueEmail(),
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());

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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());

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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
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
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        email,
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
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

    @Test
    void login_withUsernameInAnyCase_returnsTokens() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        String username = uniqueUsername();
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/signup",
                                Map.of(
                                        "name",
                                        "Jane Doe",
                                        "email",
                                        email,
                                        "password",
                                        "Password123!",
                                        "username",
                                        username),
                                ip))
                .andExpect(status().isOk());
        verifyEmailFor(email, ip);

        Map<String, String> loginRequest =
                Map.of("identifier", username.toUpperCase(), "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/login", loginRequest, ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    void login_withWrongPassword_reportsTheRealErrorCodeAndMessage() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        Map<String, String> loginRequest =
                Map.of("identifier", "nobody-here", "password", "Password123!");

        mockMvc.perform(jsonPost("/api/auth/login", loginRequest, ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(1001))
                .andExpect(
                        jsonPath("$.status.message").value("Invalid email, username or password"));
    }

    @Test
    void signup_withTakenUsername_returns400() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String username = uniqueUsername();
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/signup",
                                Map.of(
                                        "name",
                                        "Jane Doe",
                                        "email",
                                        uniqueEmail(),
                                        "password",
                                        "Password123!",
                                        "username",
                                        username),
                                ip))
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/auth/username-available")
                                .with(ip)
                                .param("username", username.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(
                        jsonPost(
                                "/api/auth/signup",
                                Map.of(
                                        "name",
                                        "John Doe",
                                        "email",
                                        uniqueEmail(),
                                        "password",
                                        "Password123!",
                                        "username",
                                        username.toUpperCase()),
                                ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.code").value(1102));
    }

    @Test
    void signup_withInvalidPayload_reportsReadableFieldErrors() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        Map<String, String> invalidRequest =
                Map.of(
                        "name", "Jane Doe",
                        "email", uniqueEmail(),
                        "password", "Password123!",
                        "username", "a@b");

        mockMvc.perform(jsonPost("/api/auth/signup", invalidRequest, ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status.message").value(UsernameRules.MESSAGE))
                .andExpect(jsonPath("$.data.username").value(UsernameRules.MESSAGE));
    }

    // Not wrapped in the class-level test transaction: consumeCode runs in its
    // own transaction and must see the code that /request committed.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emailCode_signsUpAnUnknownAddressAndSignsItIn() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();

        mockMvc.perform(jsonPost("/api/auth/email-code/request", Map.of("email", email), ip))
                .andExpect(status().isOk());

        String body =
                mockMvc.perform(
                                jsonPost(
                                        "/api/auth/email-code/verify",
                                        Map.of("email", email, "code", lastLoginCode.get()),
                                        ip))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        mockMvc.perform(
                        get("/api/auth/user")
                                .with(ip)
                                .header(
                                        "Authorization",
                                        "Bearer " + readJsonField(body, "accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        // The sign-up audit row references a user created in the same request,
        // so it can only be written once that request's transaction commits.
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(
                        auditLogRepository
                                .findAllByUserIdOrderByCreatedAtDesc(
                                        user.getId(), Pageable.unpaged())
                                .map(AuditLog::getEventType))
                .contains(AuditEventType.SIGNUP, AuditEventType.LOGIN_SUCCESS);

        // One-time: the same code can't be used again.
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/email-code/verify",
                                Map.of("email", email, "code", lastLoginCode.get()),
                                ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void supabaseToken_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/supabase-token").with(uniqueClientIp()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emailCode_withAWrongCode_returns401WithItsErrorCode() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        mockMvc.perform(jsonPost("/api/auth/email-code/request", Map.of("email", email), ip))
                .andExpect(status().isOk());
        String wrong = "000000".equals(lastLoginCode.get()) ? "111111" : "000000";

        mockMvc.perform(
                        jsonPost(
                                "/api/auth/email-code/verify",
                                Map.of("email", email, "code", wrong),
                                ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status.code").value(1012));
    }

    @Test
    void supabaseToken_whenBridgeNotConfigured_returns503() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        Map<String, String> signUpRequest =
                Map.of(
                        "name",
                        "Jane Doe",
                        "email",
                        uniqueEmail(),
                        "password",
                        "Password123!",
                        "username",
                        uniqueUsername());
        String signUpBody =
                mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String accessToken = readJsonField(signUpBody, "accessToken");

        // The test profile sets no SUPABASE_* values, so the bridge stays off.
        mockMvc.perform(
                        post("/api/auth/supabase-token")
                                .with(ip)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isServiceUnavailable());
    }

    @SuppressWarnings("unchecked")
    private String readJsonField(String responseBody, String field) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        return (String) data.get(field);
    }
}
