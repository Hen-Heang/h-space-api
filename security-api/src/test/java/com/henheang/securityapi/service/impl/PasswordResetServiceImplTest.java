package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.securityapi.domain.PasswordResetToken;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.repository.PasswordResetTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.SecureTokenGenerator;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.service.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceImplTest {

    private PasswordResetTokenRepository tokenRepository;
    private RefreshTokenService refreshTokenService;
    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(PasswordResetTokenRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hashed");
        service =
                new PasswordResetServiceImpl(
                        mock(UserRepository.class),
                        mock(EmailService.class),
                        tokenRepository,
                        encoder,
                        mock(AuditLogService.class),
                        refreshTokenService,
                        mock(SecureTokenGenerator.class));
    }

    private PasswordResetToken resetToken(User user, LocalDateTime expiry) {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("reset-token");
        token.setUser(user);
        token.setExpiryDateTime(expiry);
        when(tokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void resetPassword_endsEverySessionOfTheUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("synthetic@example.test");
        resetToken(user, LocalDateTime.now().plusMinutes(10));

        service.resetPassword("reset-token", "New-synthetic-password-1");

        verify(refreshTokenService).revokeAllUserTokens(user);
    }

    @Test
    void resetPassword_withAnExpiredToken_revokesNothing() {
        User user = new User();
        user.setId(UUID.randomUUID());
        resetToken(user, LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.resetPassword("reset-token", "New-synthetic-password-1"))
                .isInstanceOf(AuthException.class);

        verify(refreshTokenService, never()).revokeAllUserTokens(any());
    }
}
