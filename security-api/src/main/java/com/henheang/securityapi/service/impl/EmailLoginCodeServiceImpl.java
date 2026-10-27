package com.henheang.securityapi.service.impl;

import com.henheang.apicore.components.common.api.ExitCode;
import com.henheang.securityapi.domain.EmailLoginCode;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.repository.EmailLoginCodeRepository;
import com.henheang.securityapi.service.EmailLoginCodeService;
import com.henheang.securityapi.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailLoginCodeServiceImpl implements EmailLoginCodeService {

    private static final Logger logger = LoggerFactory.getLogger(EmailLoginCodeServiceImpl.class);

    private final EmailLoginCodeRepository emailLoginCodeRepository;
    private final EmailService emailService;
    private final Duration codeTtl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public EmailLoginCodeServiceImpl(
            EmailLoginCodeRepository emailLoginCodeRepository,
            EmailService emailService,
            @Value("${app.email-login.code-expiration-minutes:10}") long codeExpirationMinutes,
            @Value("${app.email-login.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.email-login.max-attempts:5}") int maxAttempts) {
        this(
                emailLoginCodeRepository,
                emailService,
                Duration.ofMinutes(codeExpirationMinutes),
                Duration.ofSeconds(resendCooldownSeconds),
                maxAttempts,
                Clock.systemUTC());
    }

    EmailLoginCodeServiceImpl(
            EmailLoginCodeRepository emailLoginCodeRepository,
            EmailService emailService,
            Duration codeTtl,
            Duration resendCooldown,
            int maxAttempts,
            Clock clock) {
        this.emailLoginCodeRepository = emailLoginCodeRepository;
        this.emailService = emailService;
        this.codeTtl = codeTtl;
        this.resendCooldown = resendCooldown;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void sendCode(String rawEmail) {
        String email = EmailLoginCodeService.normalizeEmail(rawEmail);
        Instant now = clock.instant();

        Optional<EmailLoginCode> existing = emailLoginCodeRepository.findByEmail(email);
        if (existing.isPresent()
                && !existing.get().isExpired(now)
                && existing.get().getCreatedAt() != null
                && existing.get().getCreatedAt().plus(resendCooldown).isAfter(now)) {
            logger.info("Sign-in code re-requested within cooldown for {}", email);
            return;
        }
        existing.ifPresent(
                code -> {
                    emailLoginCodeRepository.delete(code);
                    // The unique index on email needs the old row gone before
                    // the replacement is inserted in the same transaction.
                    emailLoginCodeRepository.flush();
                });

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        EmailLoginCode loginCode = new EmailLoginCode();
        loginCode.setEmail(email);
        loginCode.setCodeHash(hash(email, code));
        loginCode.setExpiresAt(now.plus(codeTtl));
        emailLoginCodeRepository.save(loginCode);

        if (!emailService.sendLoginCodeEmail(email, code, codeTtl.toMinutes())) {
            logger.error("Failed to send sign-in code to {}", email);
            emailLoginCodeRepository.delete(loginCode);
            throw new AuthException(
                    ExitCode.SYSTEM_ERROR, "Couldn't send the sign-in code. Try again later.");
        }
        logger.info("Sign-in code sent to {}", email);
    }

    // Own transaction: the caller throws after a wrong code, and rolling that
    // back must not undo the recorded attempt (or the attempt cap is useless).
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consumeCode(String rawEmail, String code) {
        String email = EmailLoginCodeService.normalizeEmail(rawEmail);
        Optional<EmailLoginCode> found = emailLoginCodeRepository.findByEmail(email);
        if (found.isEmpty()) {
            return false;
        }

        EmailLoginCode loginCode = found.get();
        if (loginCode.isExpired(clock.instant())) {
            emailLoginCodeRepository.delete(loginCode);
            return false;
        }

        boolean matches =
                MessageDigest.isEqual(
                        loginCode.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                        hash(email, code).getBytes(StandardCharsets.US_ASCII));
        if (matches) {
            emailLoginCodeRepository.delete(loginCode);
            return true;
        }

        // Six digits are guessable without a cap: after maxAttempts wrong
        // tries the code is gone and a new one has to be requested.
        loginCode.setAttempts(loginCode.getAttempts() + 1);
        if (loginCode.getAttempts() >= maxAttempts) {
            logger.warn(
                    "Sign-in code for {} discarded after {} wrong attempts", email, maxAttempts);
            emailLoginCodeRepository.delete(loginCode);
        } else {
            emailLoginCodeRepository.save(loginCode);
        }
        return false;
    }

    // Salted with the email so equal codes for different addresses differ.
    private static String hash(String email, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
