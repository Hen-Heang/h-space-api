package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaStepUpService;
import com.henheang.securityapi.service.MfaStepUpService.Result;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Step-up follows the sign-in MFA rules (once-only TOTP, one-time backup codes, wrong codes count
 * toward the shared lock). Not @Transactional: the service runs in its own transaction, which can
 * only see committed users - so users are committed here and hard-deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MfaStepUpServiceIntegrationTest {

    private static final AtomicInteger IP_SUFFIX = new AtomicInteger(1);

    @Autowired private MfaStepUpService stepUp;
    @Autowired private UserRepository userRepository;
    @Autowired private MfaBackupCodeService backupCodes;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;

    // No unlock email and no audit rows: neither is what's under test.
    @MockitoBean private AccountUnlockService accountUnlockService;
    @MockitoBean private AuditLogService auditLogService;

    private final List<User> created = new ArrayList<>();

    @AfterEach
    void deleteUsers() {
        for (User user : created) {
            backupCodes.clear(user);
            userRepository.deleteById(user.getId());
        }
    }

    private User user(String secret) {
        User user = new User();
        user.setEmail("step-up-" + UUID.randomUUID() + "@example.com");
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user.setMfaEnabled(secret != null);
        user.setMfaSecret(secret);
        user = userRepository.saveAndFlush(user);
        created.add(user);
        return user;
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    void aCurrentCodeVerifiesOnce_aReplayIsInvalidAndCounted() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        User user = user(secret);
        String code = totp(secret, currentTimeStep());

        assertThat(stepUp.verify(user.getId(), code, "test")).isEqualTo(Result.VERIFIED);
        assertThat(stepUp.verify(user.getId(), code, "test")).isEqualTo(Result.INVALID);
        assertThat(reload(user).getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void aBackupCodeWorksOnlyOnce() {
        User user = user(new DefaultSecretGenerator().generate());
        String backup = backupCodes.regenerate(user).get(0);

        assertThat(stepUp.verify(user.getId(), backup, "test")).isEqualTo(Result.VERIFIED);
        assertThat(stepUp.verify(user.getId(), backup, "test")).isEqualTo(Result.INVALID);
    }

    @Test
    void fiveWrongCodesLock_andThenEvenACorrectCodeIsRefused() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        User user = user(secret);
        String wrong = wrongTotp(secret);

        for (int i = 0; i < 4; i++) {
            assertThat(stepUp.verify(user.getId(), wrong, "test")).isEqualTo(Result.INVALID);
        }
        assertThat(stepUp.verify(user.getId(), wrong, "test")).isEqualTo(Result.LOCKED);
        assertThat(reload(user).isAccountNonLocked()).isFalse();
        assertThat(stepUp.verify(user.getId(), totp(secret, currentTimeStep()), "test"))
                .isEqualTo(Result.LOCKED);
    }

    @Test
    void aCorrectCodeClearsEarlierFailures() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        User user = user(secret);
        stepUp.verify(user.getId(), wrongTotp(secret), "test");

        assertThat(stepUp.verify(user.getId(), totp(secret, currentTimeStep()), "test"))
                .isEqualTo(Result.VERIFIED);
        assertThat(reload(user).getFailedLoginAttempts()).isZero();
    }

    @Test
    void aMissingCodeIsInvalidAndCounted_noMfaIsReportedAsSuch() {
        User user = user(new DefaultSecretGenerator().generate());

        assertThat(stepUp.verify(user.getId(), null, "test")).isEqualTo(Result.INVALID);
        assertThat(stepUp.verify(user.getId(), " ", "test")).isEqualTo(Result.INVALID);
        assertThat(reload(user).getFailedLoginAttempts()).isEqualTo(2);
        assertThat(stepUp.verify(user(null).getId(), "123456", "test"))
                .isEqualTo(Result.MFA_NOT_ENABLED);
    }

    // MFA disable used a plain verifyCode: a code could be replayed and wrong
    // guesses never counted. It now goes through step-up.
    @Test
    void mfaDisable_refusesAReplayedCode_andWrongCodesLockTheAccount() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        User user = user(secret);
        String token = jwtTokenProvider.generateToken(user);
        String used = totp(secret, currentTimeStep());
        assertThat(stepUp.verify(user.getId(), used, "test")).isEqualTo(Result.VERIFIED);

        disable(token, used).andExpect(status().isUnauthorized());
        String wrong = wrongTotp(secret);
        for (int i = 0; i < 3; i++) {
            disable(token, wrong).andExpect(jsonPath("$.status.code").value(1008));
        }
        // The fifth wrong code (replay + 4) locks.
        disable(token, wrong).andExpect(jsonPath("$.status.code").value(1002));

        User after = reload(user);
        assertThat(after.isMfaEnabled()).isTrue();
        assertThat(after.isAccountNonLocked()).isFalse();
    }

    private ResultActions disable(String token, String code) throws Exception {
        String ip = "10.12.13." + IP_SUFFIX.getAndIncrement();
        return mockMvc.perform(
                post("/api/auth/mfa/disable")
                        .with(
                                request -> {
                                    request.setRemoteAddr(ip);
                                    return request;
                                })
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"));
    }

    private static long currentTimeStep() {
        return Math.floorDiv(new SystemTimeProvider().getTime(), 30);
    }

    private static String totp(String secret, long timeStep) throws Exception {
        return new DefaultCodeGenerator().generate(secret, timeStep);
    }

    // A six-digit code valid for none of the steps the server accepts.
    private static String wrongTotp(String secret) throws Exception {
        long step = currentTimeStep();
        Set<String> valid = new HashSet<>();
        for (long s = step - 2; s <= step + 2; s++) {
            valid.add(totp(secret, s));
        }
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }
}
