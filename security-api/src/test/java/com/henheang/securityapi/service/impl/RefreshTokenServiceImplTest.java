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

    @Test
    void createRefreshToken_revokesExistingTokensAndIssuesNewOne() {
        User user = user();
        RefreshToken existing = new RefreshToken();
        existing.setRevoked(false);
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(user))
                .thenReturn(List.of(existing));

        RefreshToken created = refreshTokenService.createRefreshToken(user);

        assertThat(existing.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).save(existing);
        assertThat(created.getRawToken()).isNotBlank();
        assertThat(created.isRevoked()).isFalse();
        assertThat(created.getUser()).isEqualTo(user);
        assertThat(created.getExpiryDate())
                .isAfter(Instant.now().plus(java.time.Duration.ofDays(6)));
    }

    @Test
    void createRefreshToken_neverStoresRawTokenInPersistedHash() {
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(any())).thenReturn(List.of());

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
    void logout_throwsWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> refreshTokenService.logout("missing-token"));
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
