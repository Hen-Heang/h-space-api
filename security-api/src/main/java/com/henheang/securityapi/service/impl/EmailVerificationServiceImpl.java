package com.henheang.securityapi.service.impl;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.repository.EmailVerificationTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.SecureTokenGenerator;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.service.EmailVerificationService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final Logger logger =
            LoggerFactory.getLogger(EmailVerificationServiceImpl.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final AuditLogService auditLogService;

    @Value("${app.email-verification.token-expiration-minutes:1440}") // Default: 24 hours
    private int tokenExpirationMinutes;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public void sendVerificationEmail(User user) {
        // Replace any outstanding token so only the most recently sent link works.
        emailVerificationTokenRepository.deleteAll(
                emailVerificationTokenRepository.findByUser(user));

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(secureTokenGenerator.generate());
        verificationToken.setUser(user);
        verificationToken.setExpiryDateTime(
                LocalDateTime.now().plusMinutes(tokenExpirationMinutes));
        emailVerificationTokenRepository.save(verificationToken);

        String verificationLink =
                String.format(
                        "%s/verify-email?token=%s", frontendUrl, verificationToken.getToken());

        boolean emailSent =
                emailService.sendVerificationEmail(
                        user.getEmail(), user.getName(), verificationLink);

        if (!emailSent) {
            logger.error("Failed to send verification email to: {}", user.getEmail());
            emailVerificationTokenRepository.delete(verificationToken);
            throw new AuthException(
                    ExitCode.SYSTEM_ERROR,
                    "Failed to send verification email. Please try again later.");
        }

        auditLogService.log(
                AuditEventType.EMAIL_VERIFICATION_REQUESTED, user.getId(), user.getEmail());
        logger.info("Verification email sent to: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            logger.warn("Verification resend requested for non-existent email: {}", email);
            // Don't reveal whether the email exists - appear to succeed either way.
            return;
        }

        User user = userOptional.get();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            logger.info("Verification resend requested for already-verified email: {}", email);
            return;
        }

        sendVerificationEmail(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken =
                emailVerificationTokenRepository
                        .findByToken(token)
                        .orElseThrow(
                                () -> {
                                    logger.warn("Invalid email verification token used: {}", token);
                                    return new AuthException(
                                            ExitCode.EMAIL_VERIFICATION_TOKEN_INVALID);
                                });

        if (verificationToken.isExpired()) {
            logger.info("Expired email verification token used: {}", token);
            emailVerificationTokenRepository.delete(verificationToken);
            throw new AuthException(ExitCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);
        }

        User user = verificationToken.getUser();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            emailVerificationTokenRepository.delete(verificationToken);
            throw new AuthException(ExitCode.EMAIL_ALREADY_VERIFIED);
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete the token after a successful verification (one-time use).
        emailVerificationTokenRepository.delete(verificationToken);

        auditLogService.log(AuditEventType.EMAIL_VERIFIED, user.getId(), user.getEmail());
        logger.info("Email verified for user: {}", user.getEmail());
    }
}
