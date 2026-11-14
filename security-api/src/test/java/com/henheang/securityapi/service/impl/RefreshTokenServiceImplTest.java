package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceImplTest {

    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationString", "P7D");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private RefreshToken liveSession() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setRevoked(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        return token;
    }

    @Test
    void createRefreshToken_keepsTheUsersOtherSessionsAlive() {
        User user = user();
        RefreshToken otherDevice = liveSession();
        when(refreshTokenRepository.findActiveByUserOldestFirst(any(), any()))
                .thenReturn(List.of(otherDevice));

        RefreshToken created = refreshTokenService.createRefreshToken(user);

        assertThat(otherDevice.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(otherDevice);
        assertThat(created.getRawToken()).isNotBlank();
        assertThat(created.isRevoked()).isFalse();
        assertThat(created.getUser()).isEqualTo(user);
        assertThat(created.getExpiryDate())
                .isAfter(Instant.now().plus(java.time.Duration.ofDays(6)));
    }

    @Test
    void createRefreshToken_atTheSessionCap_dropsOnlyTheOldestSession() {
        ReflectionTestUtils.setField(refreshTokenService, "maxActiveSessionsPerUser", 3);
        RefreshToken oldest = liveSession();
        RefreshToken middle = liveSession();
        RefreshToken newest = liveSession();
        when(refreshTokenRepository.findActiveByUserOldestFirst(any(), any()))
                .thenReturn(List.of(oldest, middle, newest));

        refreshTokenService.createRefreshToken(user());

        assertThat(oldest.isRevoked()).isTrue();
        assertThat(middle.isRevoked()).isFalse();
        assertThat(newest.isRevoked()).isFalse();
    }

    @Test
    void createRefreshToken_belowTheSessionCap_dropsNothing() {
        ReflectionTestUtils.setField(refreshTokenService, "maxActiveSessionsPerUser", 3);
        RefreshToken a = liveSession();
        RefreshToken b = liveSession();
        when(refreshTokenRepository.findActiveByUserOldestFirst(any(), any()))
                .thenReturn(List.of(a, b));

        refreshTokenService.createRefreshToken(user());

        assertThat(a.isRevoked()).isFalse();
        assertThat(b.isRevoked()).isFalse();
    }

    @Test
    void createRefreshToken_neverStoresRawTokenInPersistedHash() {
        RefreshToken created = refreshTokenService.createRefreshToken(user());

        assertThat(created.getTokenHash()).isNotEqualTo(created.getRawToken());
    }

    @Test
    void validateRefreshToken_returnsEmptyWhenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(refreshTokenService.validateRefreshToken("missing-token")).isEmpty();
    }

    @Test
    void validateRefreshToken_filtersOutRevokedTokens() {
        RefreshToken revoked = new RefreshToken();
        revoked.setRevoked(true);
        revoked.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThat(refreshTokenService.validateRefreshToken("some-token")).isEmpty();
    }

    @Test
    void validateRefreshToken_filtersOutExpiredTokens() {
        RefreshToken expired = new RefreshToken();
        expired.setRevoked(false);
        expired.setExpiryDate(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThat(refreshTokenService.validateRefreshToken("some-token")).isEmpty();
    }

    @Test
    void validateRefreshToken_returnsTokenWhenValid() {
        RefreshToken valid = new RefreshToken();
        valid.setRevoked(false);
        valid.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(valid));

        assertThat(refreshTokenService.validateRefreshToken("some-token")).contains(valid);
    }

    @Test
    void logout_revokesMatchingToken() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        refreshTokenService.logout("raw-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_isANoOpWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.logout("missing-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken activeTokenFor(User user) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setRevoked(false);
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void rotateRefreshToken_spendsTheTokenAndIssuesANewOne() {
        User user = user();
        user.setEmailVerified(true);
        RefreshToken current = activeTokenFor(user);
        when(refreshTokenRepository.revokeIfActive(current.getId())).thenReturn(1);

        Optional<RefreshToken> rotated = refreshTokenService.rotateRefreshToken("raw-token");

        assertThat(rotated).isPresent();
        assertThat(rotated.get().getRawToken()).isNotBlank();
        assertThat(rotated.get().getUser()).isEqualTo(user);
    }

    @Test
    void rotateRefreshToken_leavesTheUsersOtherSessionsAlone() {
        User user = user();
        user.setEmailVerified(true);
        RefreshToken current = activeTokenFor(user);
        RefreshToken otherDevice = liveSession();
        when(refreshTokenRepository.revokeIfActive(current.getId())).thenReturn(1);
        when(refreshTokenRepository.findActiveByUserOldestFirst(any(), any()))
                .thenReturn(List.of(otherDevice));

        assertThat(refreshTokenService.rotateRefreshToken("raw-token")).isPresent();

        assertThat(otherDevice.isRevoked()).isFalse();
    }

    @Test
    void rotateRefreshToken_whenAnotherRequestSpentItFirst_issuesNothing() {
        User user = user();
        user.setEmailVerified(true);
        RefreshToken current = activeTokenFor(user);
        when(refreshTokenRepository.revokeIfActive(current.getId())).thenReturn(0);

        assertThat(refreshTokenService.rotateRefreshToken("raw-token")).isEmpty();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_forAnUnverifiedEmail_spendsNothing() {
        User user = user();
        user.setEmailVerified(false);
        activeTokenFor(user);

        assertThat(refreshTokenService.rotateRefreshToken("raw-token")).isEmpty();
        verify(refreshTokenRepository, never()).revokeIfActive(any());
    }

    @Test
    void revokeAllUserTokens_revokesEachValidToken() {
        RefreshToken t1 = new RefreshToken();
        RefreshToken t2 = new RefreshToken();
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(any()))
                .thenReturn(List.of(t1, t2));

        refreshTokenService.revokeAllUserTokens(user());

        assertThat(t1.isRevoked()).isTrue();
        assertThat(t2.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void revokeAllUserTokens_doesNothingWhenNoTokens() {
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(any())).thenReturn(List.of());

        refreshTokenService.revokeAllUserTokens(user());

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
