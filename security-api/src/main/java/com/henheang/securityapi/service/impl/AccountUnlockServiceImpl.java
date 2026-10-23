package com.henheang.securityapi.service.impl;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AccountUnlockToken;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.repository.AccountUnlockTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.SecureTokenGenerator;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountUnlockServiceImpl implements AccountUnlockService {

    private static final Logger logger = LoggerFactory.getLogger(AccountUnlockServiceImpl.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AccountUnlockTokenRepository accountUnlockTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final AuditLogService auditLogService;

    @Value("${app.account-lock.unlock-token-expiration-minutes:60}")
    private int tokenExpirationMinutes;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public void sendUnlockEmail(User user) {
        accountUnlockTokenRepository.deleteAll(accountUnlockTokenRepository.findByUser(user));

        AccountUnlockToken unlockToken = new AccountUnlockToken();
        unlockToken.setToken(secureTokenGenerator.generate());
        unlockToken.setUser(user);
        unlockToken.setExpiryDateTime(LocalDateTime.now().plusMinutes(tokenExpirationMinutes));
        accountUnlockTokenRepository.save(unlockToken);

        String unlockLink =
                String.format("%s/unlock-account?token=%s", frontendUrl, unlockToken.getToken());

        boolean emailSent =
                emailService.sendAccountLockedEmail(user.getEmail(), user.getName(), unlockLink);

        if (!emailSent) {
            logger.error("Failed to send account unlock email to: {}", user.getEmail());
            accountUnlockTokenRepository.delete(unlockToken);
            return;
        }

        auditLogService.log(AuditEventType.ACCOUNT_UNLOCK_REQUESTED, user.getId(), user.getEmail());
        logger.info("Account unlock email sent to: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendUnlockEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            logger.warn("Unlock resend requested for non-existent email: {}", email);
            return;
        }

        User user = userOptional.get();
        if (user.isAccountNonLocked()) {
            logger.info("Unlock resend requested for a non-locked account: {}", email);
            return;
        }

        sendUnlockEmail(user);
    }

    @Override
    @Transactional
    public void unlockAccount(String token) {
        AccountUnlockToken unlockToken =
                accountUnlockTokenRepository
                        .findByToken(token)
                        .orElseThrow(
                                () -> {
                                    logger.warn("Invalid account unlock token used: {}", token);
                                    return new AuthException(ExitCode.ACCOUNT_UNLOCK_TOKEN_INVALID);
                                });

        if (unlockToken.isExpired()) {
            logger.info("Expired account unlock token used: {}", token);
            accountUnlockTokenRepository.delete(unlockToken);
            throw new AuthException(ExitCode.ACCOUNT_UNLOCK_TOKEN_EXPIRED);
        }

        User user = unlockToken.getUser();
        clearLock(user);

        // Delete the token after a successful unlock (one-time use).
        accountUnlockTokenRepository.delete(unlockToken);

        auditLogService.log(AuditEventType.ACCOUNT_UNLOCKED, user.getId(), user.getEmail());
        logger.info("Account unlocked via self-service token for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void adminUnlock(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.isAccountNonLocked()) {
            throw new AuthException(ExitCode.ACCOUNT_NOT_LOCKED);
        }

        clearLock(user);
        accountUnlockTokenRepository.deleteAll(accountUnlockTokenRepository.findByUser(user));

        auditLogService.log(
                AuditEventType.ACCOUNT_UNLOCKED, user.getId(), user.getEmail(), "by admin");
        logger.info("Account unlocked by admin for user: {}", user.getEmail());
    }

    private void clearLock(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
