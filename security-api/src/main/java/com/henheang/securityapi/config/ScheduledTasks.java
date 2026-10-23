package com.henheang.securityapi.config;

import com.henheang.securityapi.repository.PasswordResetTokenRepository;
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

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ScheduledTasks {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    /** Clean up expired password reset tokens daily at midnight */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredPasswordResetTokens() {
        logger.info("Running scheduled cleanup of expired password reset tokens");
        passwordResetTokenRepository.deleteAllExpiredTokens(LocalDateTime.now());
    }

    /**
     * Purge blacklist entries whose underlying access token has already expired - the token would
     * be rejected on expiry alone, so the row is dead weight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupExpiredRevokedTokens() {
        logger.info("Running scheduled cleanup of expired revoked-token entries");
        revokedTokenRepository.deleteAll(
                revokedTokenRepository.findAllByExpiryDateBefore(Instant.now()));
    }
}
