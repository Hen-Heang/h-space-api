package com.henheang.securityapi.config;

import com.henheang.securityapi.repository.AccountUnlockTokenRepository;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.PasswordResetTokenRepository;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import com.henheang.securityapi.repository.RevokedTokenRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes expired auth tokens. An expired token is already rejected on expiry alone, so its row is
 * dead weight - deleting it changes no behaviour, which also makes the job safe to run twice.
 *
 * <p>Schedule: {@code app.scheduling.token-cleanup-cron} (default daily at midnight; {@code "-"}
 * turns it off) in {@code app.scheduling.zone} (default UTC), so the run time doesn't depend on the
 * host's timezone. Guarded by {@link ScheduledJobLock}: this class is also picked up by every
 * module that scans {@code com.henheang.securityapi}, and only one of them should do the work.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {

    static final String TOKEN_CLEANUP_JOB = "security-api.expired-token-cleanup";

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    private final ScheduledJobLock jobLock;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AccountUnlockTokenRepository accountUnlockTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    @Scheduled(
            cron = "${app.scheduling.token-cleanup-cron:0 0 0 * * *}",
            zone = "${app.scheduling.zone:UTC}")
    @Transactional
    public void cleanupExpiredTokens() {
        if (!jobLock.tryAcquire(TOKEN_CLEANUP_JOB)) {
            logger.debug("Skipping {}: another instance is running it", TOKEN_CLEANUP_JOB);
            return;
        }
        // The three emailed-link tokens store a LocalDateTime written by this same JVM clock;
        // refresh and revoked tokens store an Instant.
        LocalDateTime nowLocal = LocalDateTime.now();
        Instant now = Instant.now();

        int passwordReset = passwordResetTokenRepository.deleteAllExpiredTokens(nowLocal);
        int accountUnlock = accountUnlockTokenRepository.deleteAllExpiredTokens(nowLocal);
        int emailVerification = emailVerificationTokenRepository.deleteAllExpiredTokens(nowLocal);
        int refresh = refreshTokenRepository.deleteAllExpiredTokens(now);
        int revoked = revokedTokenRepository.deleteAllExpiredTokens(now);

        // Counts only - never token values.
        logger.info(
                "Expired-token cleanup: password-reset={}, account-unlock={},"
                        + " email-verification={}, refresh={}, revoked={}",
                passwordReset,
                accountUnlock,
                emailVerification,
                refresh,
                revoked);
    }
}
