package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import com.henheang.securityapi.service.AuditLogService;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token.expiration}")
    private String refreshTokenExpirationString;

    // Each sign-in is its own session: signing in on a phone must not end the
    // laptop's session. Bounded so abandoned sign-ins can't pile up forever.
    @Value("${jwt.refresh-token.max-active-per-user:10}")
    private int maxActiveSessionsPerUser = 10;

    // A spent token replayed within this window of its rotation is refused but
    // not treated as theft: two tabs refreshing at once race exactly like this.
    // Past it, a replay revokes the token's whole family.
    @Value("${jwt.refresh-token.reuse-grace:PT10S}")
    private Duration reuseGrace = Duration.ofSeconds(10);

    // A sign-in starts a new family; rotation keeps the family.
    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        return createRefreshToken(user, UUID.randomUUID());
    }

    private RefreshToken createRefreshToken(User user, UUID familyId) {
        // Keep room for the new session: if the user is at the cap, drop the
        // oldest live ones. Rotation spends its own token first, so replacing
        // a session never evicts another.
        List<RefreshToken> active =
                refreshTokenRepository.findActiveByUserOldestFirst(user, Instant.now());
        int excess = active.size() - (maxActiveSessionsPerUser - 1);
        for (int i = 0; i < excess; i++) {
            RefreshToken oldest = active.get(i);
            oldest.setRevoked(true);
            refreshTokenRepository.save(oldest);
        }

        // Create a new refresh token. Only its SHA-256 hash is persisted, so a
        // database leak doesn't hand out a usable token; the raw value is
        // returned to the caller once and never stored.
        byte[] rawBytes = new byte[32];
        secureRandom.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setFamilyId(familyId);
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

    @Override
    @Transactional
    public Optional<RefreshToken> rotateRefreshToken(String token) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        if (current.isRevoked()) {
            revokeFamilyOnReuse(current);
            return Optional.empty();
        }
        if (!current.getExpiryDate().isAfter(Instant.now())
                // Same rule as JwtAuthenticationFilter: no session for an
                // unverified email.
                || !Boolean.TRUE.equals(current.getUser().getEmailVerified())
                // Of two requests racing with the same token, only one spends
                // it here; the other gets 0 and is refused.
                || refreshTokenRepository.spendForRotation(current.getId(), Instant.now()) != 1) {
            return Optional.empty();
        }
        return Optional.of(createRefreshToken(current.getUser(), current.getFamilyId()));
    }

    // A token spent by rotation came back: either the client replayed an old
    // one, or someone copied it. The server can't tell which holder is the
    // attacker, so it ends the whole chain and the user signs in again. Runs in
    // the caller's transaction, which commits: the controller throws only
    // after rotateRefreshToken has returned.
    private void revokeFamilyOnReuse(RefreshToken spent) {
        Instant rotatedAt = spent.getRotatedAt();
        if (rotatedAt == null || rotatedAt.plus(reuseGrace).isAfter(Instant.now())) {
            return;
        }
        int revoked = refreshTokenRepository.revokeFamily(spent.getFamilyId());
        UUID userId = spent.getUser().getId();
        log.warn(
                "Refresh token reuse for user {}: revoked {} live token(s) of family {}",
                userId,
                revoked,
                spent.getFamilyId());
        auditLogService.log(
                AuditEventType.REFRESH_TOKEN_REUSE_DETECTED,
                userId,
                null,
                "familyId=" + spent.getFamilyId() + ", revoked=" + revoked);
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

    @Override
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

    // Idempotent: an unknown or already-revoked token is already as signed
    // out as logout can make it.
    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository
                .findByTokenHash(hash(refreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(
                        token -> {
                            token.setRevoked(true);
                            refreshTokenRepository.save(token);
                        });
    }
}
