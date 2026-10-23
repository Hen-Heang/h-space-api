package com.henheang.securityapi.service.impl;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.PasswordResetToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.repository.PasswordResetTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.SecureTokenGenerator;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.service.PasswordResetService;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SecureTokenGenerator secureTokenGenerator;

    @Value("${app.reset-password.token-expiration-minutes:1440}") // Default: 24 hours
    private int tokenExpirationMinutes;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public void createPasswordResetTokenEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            logger.warn("Password reset requested for non-existent email: {}", email);
            // For security reasons, we don't reveal whether the email exists or not
            // The service will appear to succeed, but no email will be sent
            return;
        }

        User user = userOptional.get();
        logger.info("Creating password reset token for user: {}", email);

        // Delete any existing password reset tokens for this user
        passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findByUser(user));

        // Create a new token
        String resetToken = secureTokenGenerator.generate();
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(resetToken);
        passwordResetToken.setUser(user);
        passwordResetToken.setExpiryDateTime(
                LocalDateTime.now().plusMinutes(tokenExpirationMinutes));

        passwordResetTokenRepository.save(passwordResetToken);

        // Construct reset password link
        String resetPasswordLink =
                String.format("%s/reset-password?token=%s", frontendUrl, resetToken);
        logger.info("Generated reset password link for user: {}", email);

        // Send the email
        try {
            boolean emailSent =
                    emailService.sendPasswordResetEmail(
                            user.getEmail(), user.getName(), resetPasswordLink);

            if (!emailSent) {
                logger.error("Failed to send password reset email to: {}", email);
                // Delete the token since email couldn't be sent
                passwordResetTokenRepository.delete(passwordResetToken);
                throw new AuthException(
                        ExitCode.SYSTEM_ERROR,
                        "Failed to send password reset email. Please try again later.");
            }

            logger.info("Password reset email sent successfully to: {}", email);
            auditLogService.log(
                    AuditEventType.PASSWORD_RESET_REQUESTED, user.getId(), user.getEmail());
        } catch (Exception e) {
            logger.error("Error sending password reset email to {}: {}", email, e.getMessage());
            // Delete the token since email couldn't be sent
            passwordResetTokenRepository.delete(passwordResetToken);
            throw new AuthException(
                    ExitCode.SYSTEM_ERROR,
                    "Failed to send password reset email. Please try again later.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOptional =
                passwordResetTokenRepository.findByToken(token);

        if (tokenOptional.isEmpty()) {
            logger.warn("Invalid password reset token attempted: {}", token);
            return false;
        }

        PasswordResetToken passwordResetToken = tokenOptional.get();
        boolean isValid = !passwordResetToken.isExpired();

        if (!isValid) {
            logger.info("Expired password reset token attempted: {}", token);
        } else {
            logger.info("Valid password reset token validated: {}", token);
        }

        return isValid;
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken passwordResetToken =
                passwordResetTokenRepository
                        .findByToken(token)
                        .orElseThrow(
                                () -> {
                                    logger.warn("Invalid password reset token used: {}", token);
                                    return new AuthException(ExitCode.PASSWORD_RESET_TOKEN_INVALID);
                                });

        if (passwordResetToken.isExpired()) {
            logger.info("Expired password reset token used: {}", token);
            passwordResetTokenRepository.delete(passwordResetToken);
            throw new AuthException(ExitCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        User user = passwordResetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete the token after a successful password reset
        passwordResetTokenRepository.delete(passwordResetToken);

        auditLogService.log(AuditEventType.PASSWORD_RESET_COMPLETED, user.getId(), user.getEmail());
        logger.info("Password successfully reset for user: {}", user.getEmail());
    }
}
