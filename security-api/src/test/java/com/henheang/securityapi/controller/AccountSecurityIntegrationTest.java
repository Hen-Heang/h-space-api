package com.henheang.securityapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.securityapi.domain.AccountUnlockToken;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.AccountUnlockTokenRepository;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
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
 * Exercises email verification and account lock/unlock over HTTP against a real (test-profile)
 * database. Each test uses a distinct fake client IP so the shared RateLimitingFilter bucket (5
 * req/min per IP+path) never interferes between test methods.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountUnlockTokenRepository accountUnlockTokenRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockitoBean private EmailService emailService;

    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(100);

    @BeforeEach
    void stubEmailService() {
        when(emailService.sendPasswordResetEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendAccountLockedEmail(any(), any(), any())).thenReturn(true);
        when(emailService.sendAccountUnlockedEmail(any(), any())).thenReturn(true);
    }

    private RequestPostProcessor uniqueClientIp() {
        String ip = "10.11.13." + IP_SUFFIX.getAndIncrement();
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

    private void signUp(String email, RequestPostProcessor ip) throws Exception {
        Map<String, String> signUpRequest =
                Map.of("name", "Jane Doe", "email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/signup", signUpRequest, ip)).andExpect(status().isOk());
    }

    @Test
    void verifyEmail_withInvalidToken_returns400() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        mockMvc.perform(jsonPost("/api/auth/verify-email", Map.of("token", "bogus-token"), ip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerification_forUnknownEmail_returns200WithoutRevealingExistence() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/resend-verification",
                                Map.of("email", uniqueEmail()),
                                ip))
                .andExpect(status().isOk());
    }

    @Test
    void unlockAccount_withInvalidToken_returns400() throws Exception {
        RequestPostProcessor ip = uniqueClientIp();
        mockMvc.perform(jsonPost("/api/auth/unlock-account", Map.of("token", "bogus-token"), ip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailedLogins_lockAccount_andSelfServiceUnlockRestoresAccess() throws Exception {
        // /api/auth/login is capped at 5 req/min per IP (RateLimitingFilter), so each
        // logical batch of login attempts below uses its own fake IP - the account
        // lock itself lives on the user row, not the caller's IP, so this doesn't
        // change what's being verified.
        RequestPostProcessor signupIp = uniqueClientIp();
        String email = uniqueEmail();
        signUp(email, signupIp);
        verifyEmail(email, signupIp);

        Map<String, String> wrongLogin = Map.of("email", email, "password", "wrong-password");
        RequestPostProcessor failIp = uniqueClientIp();
        // 5 failures crosses the lock threshold (app.account-lock.max-failed-attempts: 5).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(jsonPost("/api/auth/login", wrongLogin, failIp))
                    .andExpect(status().isUnauthorized());
        }

        User locked = userRepository.findByEmail(email).orElseThrow();
        assertThat(locked.getLockedUntil()).isNotNull();

        // Even the correct password is rejected while locked.
        Map<String, String> correctLogin = Map.of("email", email, "password", "Password123!");
        mockMvc.perform(jsonPost("/api/auth/login", correctLogin, uniqueClientIp()))
                .andExpect(status().isUnauthorized());

        AccountUnlockToken unlockToken =
                accountUnlockTokenRepository.findByUser(locked).stream().findFirst().orElseThrow();
        mockMvc.perform(
                        jsonPost(
                                "/api/auth/unlock-account",
                                Map.of("token", unlockToken.getToken()),
                                signupIp))
                .andExpect(status().isOk());

        User unlocked = userRepository.findByEmail(email).orElseThrow();
        assertThat(unlocked.getLockedUntil()).isNull();
        assertThat(unlocked.getFailedLoginAttempts()).isZero();

        mockMvc.perform(jsonPost("/api/auth/login", correctLogin, uniqueClientIp()))
                .andExpect(status().isOk());
    }

    @Test
    void unlockEndpoint_withoutAdminRole_returns403() throws Exception {
        // Business logic for a real unlock is covered at the service layer
        // (AccountUnlockServiceImplTest); this verifies the @PreAuthorize
        // wiring on the endpoint itself - a non-admin (even the target's own
        // account) must not be able to call it.
        RequestPostProcessor ip = uniqueClientIp();
        String email = uniqueEmail();
        signUp(email, ip);
        verifyEmail(email, ip);
        User self = userRepository.findByEmail(email).orElseThrow();

        String selfToken = jwtTokenProvider.generateToken(self);

        mockMvc.perform(
                        patch("/api/users/{id}/unlock", self.getId())
                                .with(ip)
                                .header("Authorization", "Bearer " + selfToken))
                .andExpect(status().isForbidden());
    }

    private void verifyEmail(String email, RequestPostProcessor ip) throws Exception {
        // Reuses the EmailVerificationServiceImpl via the controller endpoint;
        // pull the token straight from the DB since the email itself is mocked.
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token =
                emailVerificationTokenRepository.findByUser(user).stream()
                        .findFirst()
                        .orElseThrow();
        mockMvc.perform(jsonPost("/api/auth/verify-email", Map.of("token", token.getToken()), ip))
                .andExpect(status().isOk());
    }
}
