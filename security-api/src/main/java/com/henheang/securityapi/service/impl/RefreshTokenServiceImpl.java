package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import com.henheang.securityapi.service.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token.expiration}")
    private String refreshTokenExpirationString;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // First, revoke all existing tokens for this user
        List<RefreshToken> existingTokens =
                refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        for (RefreshToken token : existingTokens) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        }

        // Create a new refresh token. Only its SHA-256 hash is persisted, so a
        // database leak doesn't hand out a usable token; the raw value is
        // returned to the caller once and never stored.
        byte[] rawBytes = new byte[32];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setRevoked(false);
        Duration duration = Duration.parse(refreshTokenExpirationString);
        refreshToken.setExpiryDate(Instant.now().plus(duration));

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        saved.setRawToken(rawToken);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> validateRefreshToken(String token) {
        return refreshTokenRepository
                .findByTokenHash(hash(token))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .filter(refreshToken -> refreshToken.getExpiryDate().isAfter(Instant.now()));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        List<RefreshToken> validTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        if (validTokens.isEmpty()) {
            return;
        }

        for (RefreshToken token : validTokens) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 0 * * ?") // Run at midnight every day
    public void deleteExpiredTokens() {
        Instant now = Instant.now();
        List<RefreshToken> expiredTokens =
                refreshTokenRepository.findAll().stream()
                        .filter(token -> token.getExpiryDate().isBefore(now))
                        .toList();
        refreshTokenRepository.deleteAll(expiredTokens);
    }

    @Override
    public void logout(String refreshToken) {
        RefreshToken token =
                refreshTokenRepository
                        .findByTokenHash(hash(refreshToken))
                        .orElseThrow(() -> new RuntimeException("Refresh token not found"));
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }
}
