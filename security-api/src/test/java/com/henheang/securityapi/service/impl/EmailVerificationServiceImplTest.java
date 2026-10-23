package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationServiceImplTest {

    private UserRepository userRepository;
    private EmailService emailService;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private SecureTokenGenerator secureTokenGenerator;
    private AuditLogService auditLogService;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        secureTokenGenerator = mock(SecureTokenGenerator.class);
        auditLogService = mock(AuditLogService.class);

        service =
                new EmailVerificationServiceImpl(
                        userRepository,
                        emailService,
                        emailVerificationTokenRepository,
                        secureTokenGenerator,
                        auditLogService);
        ReflectionTestUtils.setField(service, "tokenExpirationMinutes", 1440);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setName("Jane");
        return user;
    }

    @Test
    void sendVerificationEmail_createsTokenAndSendsEmail() {
        User user = user();
        when(emailVerificationTokenRepository.findByUser(user)).thenReturn(List.of());
        when(secureTokenGenerator.generate()).thenReturn("secure-token");
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);

        service.sendVerificationEmail(user);

        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(auditLogService)
                .log(AuditEventType.EMAIL_VERIFICATION_REQUESTED, user.getId(), user.getEmail());
    }

    @Test
    void sendVerificationEmail_whenSendFails_deletesTokenAndThrows() {
        User user = user();
        when(emailVerificationTokenRepository.findByUser(user)).thenReturn(List.of());
        when(secureTokenGenerator.generate()).thenReturn("secure-token");
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.sendVerificationEmail(user))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.SYSTEM_ERROR));

        verify(emailVerificationTokenRepository).delete(any(EmailVerificationToken.class));
    }

    @Test
    void resendVerificationEmail_forAlreadyVerifiedUser_doesNothing() {
        User user = user();
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resendVerificationEmail(user.getEmail());

        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void resendVerificationEmail_forUnknownEmail_doesNothingAndDoesNotThrow() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.resendVerificationEmail("missing@example.com");

        verify(emailVerificationTokenRepository, never()).save(any());
    }

    @Test
    void verifyEmail_withValidToken_marksUserVerifiedAndDeletesToken() {
        User user = user();
        user.setEmailVerified(false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiryDateTime(LocalDateTime.now().plusHours(1));
        when(emailVerificationTokenRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(token));

        service.verifyEmail("valid-token");

        assertThat(user.getEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).delete(token);
        verify(auditLogService).log(AuditEventType.EMAIL_VERIFIED, user.getId(), user.getEmail());
    }

    @Test
    void verifyEmail_withExpiredToken_throwsAndDeletesToken() {
        User user = user();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setExpiryDateTime(LocalDateTime.now().minusHours(1));
        when(emailVerificationTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail("expired-token"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.EMAIL_VERIFICATION_TOKEN_EXPIRED));

        verify(emailVerificationTokenRepository).delete(token);
    }

    @Test
    void verifyEmail_withUnknownToken_throws() {
        when(emailVerificationTokenRepository.findByToken("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("bogus"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.EMAIL_VERIFICATION_TOKEN_INVALID));
    }
}
