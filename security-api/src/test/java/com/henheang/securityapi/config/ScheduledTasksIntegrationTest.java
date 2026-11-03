package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.henheang.securityapi.domain.AccountUnlockToken;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.PasswordResetToken;
import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.RevokedToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.AccountUnlockTokenRepository;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.PasswordResetTokenRepository;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import com.henheang.securityapi.repository.RevokedTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the token-cleanup job against the real Flyway schema. Each test is rolled back, so the
 * synthetic rows (and anything the job deleted) never outlive it.
 */
@SpringBootTest
@Transactional
class ScheduledTasksIntegrationTest {

    @Autowired private ScheduledTasks scheduledTasks;
    @Autowired private DataSource dataSource;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private AccountUnlockTokenRepository accountUnlockTokenRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RevokedTokenRepository revokedTokenRepository;

    private User user;

    @BeforeEach
    void createUser() {
        User u = new User();
        u.setEmail("cleanup-" + UUID.randomUUID() + "@example.com");
        u.setProvider(AuthProvider.LOCAL);
        u.setEmailVerified(true);
        user = userRepository.save(u);
    }

    private PasswordResetToken passwordReset(LocalDateTime expiry) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setExpiryDateTime(expiry);
        return passwordResetTokenRepository.save(t);
    }

    private AccountUnlockToken accountUnlock(LocalDateTime expiry) {
        AccountUnlockToken t = new AccountUnlockToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setExpiryDateTime(expiry);
        return accountUnlockTokenRepository.save(t);
    }

    private EmailVerificationToken emailVerification(LocalDateTime expiry) {
        EmailVerificationToken t = new EmailVerificationToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setExpiryDateTime(expiry);
        return emailVerificationTokenRepository.save(t);
    }

    private RefreshToken refresh(Instant expiry) {
        RefreshToken t = new RefreshToken();
        t.setTokenHash(UUID.randomUUID().toString());
        t.setUser(user);
        t.setExpiryDate(expiry);
        return refreshTokenRepository.save(t);
    }

    private RevokedToken revoked(Instant expiry) {
        RevokedToken t = new RevokedToken();
        t.setJti(UUID.randomUUID().toString());
        t.setExpiryDate(expiry);
        t.setRevokedAt(Instant.now());
        return revokedTokenRepository.save(t);
    }

    @Test
    void cleanup_deletesExpiredTokens_andKeepsLiveOnes() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        LocalDateTime future = LocalDateTime.now().plusHours(1);
        Instant pastInstant = Instant.now().minus(Duration.ofHours(1));
        Instant futureInstant = Instant.now().plus(Duration.ofHours(1));

        UUID expiredReset = passwordReset(past).getId();
        UUID liveReset = passwordReset(future).getId();
        UUID expiredUnlock = accountUnlock(past).getId();
        UUID liveUnlock = accountUnlock(future).getId();
        UUID expiredVerification = emailVerification(past).getId();
        UUID liveVerification = emailVerification(future).getId();
        UUID expiredRefresh = refresh(pastInstant).getId();
        UUID liveRefresh = refresh(futureInstant).getId();
        UUID expiredRevoked = revoked(pastInstant).getId();
        UUID liveRevoked = revoked(futureInstant).getId();

        scheduledTasks.cleanupExpiredTokens();

        assertThat(passwordResetTokenRepository.existsById(expiredReset)).isFalse();
        assertThat(passwordResetTokenRepository.existsById(liveReset)).isTrue();
        assertThat(accountUnlockTokenRepository.existsById(expiredUnlock)).isFalse();
        assertThat(accountUnlockTokenRepository.existsById(liveUnlock)).isTrue();
        assertThat(emailVerificationTokenRepository.existsById(expiredVerification)).isFalse();
        assertThat(emailVerificationTokenRepository.existsById(liveVerification)).isTrue();
        assertThat(refreshTokenRepository.existsById(expiredRefresh)).isFalse();
        assertThat(refreshTokenRepository.existsById(liveRefresh)).isTrue();
        assertThat(revokedTokenRepository.existsById(expiredRevoked)).isFalse();
        assertThat(revokedTokenRepository.existsById(liveRevoked)).isTrue();
    }

    @Test
    void cleanup_isIdempotent() {
        refresh(Instant.now().minus(Duration.ofHours(1)));

        scheduledTasks.cleanupExpiredTokens();
        scheduledTasks.cleanupExpiredTokens();
        // no exception, nothing left to delete - a repeat run on another instance is harmless
    }

    @Test
    void cleanup_skips_whileAnotherInstanceHoldsTheLock() throws Exception {
        UUID expired = refresh(Instant.now().minus(Duration.ofHours(1))).getId();
        refreshTokenRepository.flush();

        // Another "instance": its own connection and transaction, holding the job's lock.
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement lock =
                    other.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lock.setString(1, ScheduledTasks.TOKEN_CLEANUP_JOB);
                lock.execute();
            }

            scheduledTasks.cleanupExpiredTokens();

            assertThat(refreshTokenRepository.existsById(expired)).isTrue();
            // Ending the other transaction releases the lock - no unlock call needed.
            other.rollback();
        }

        scheduledTasks.cleanupExpiredTokens();
        assertThat(refreshTokenRepository.existsById(expired)).isFalse();
    }
}
