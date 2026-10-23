package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AccountUnlockToken;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.repository.AccountUnlockTokenRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.SecureTokenGenerator;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccountUnlockServiceImplTest {

    private UserRepository userRepository;
    private EmailService emailService;
    private AccountUnlockTokenRepository accountUnlockTokenRepository;
    private SecureTokenGenerator secureTokenGenerator;
    private AuditLogService auditLogService;
    private AccountUnlockServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        accountUnlockTokenRepository = mock(AccountUnlockTokenRepository.class);
        secureTokenGenerator = mock(SecureTokenGenerator.class);
        auditLogService = mock(AuditLogService.class);

        service =
                new AccountUnlockServiceImpl(
                        userRepository,
                        emailService,
                        accountUnlockTokenRepository,
                        secureTokenGenerator,
                        auditLogService);
        ReflectionTestUtils.setField(service, "tokenExpirationMinutes", 60);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
    }

    private User lockedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setName("Jane");
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().plusSeconds(900));
        return user;
    }

    @Test
    void sendUnlockEmail_createsTokenAndSendsEmail() {
        User user = lockedUser();
        when(accountUnlockTokenRepository.findByUser(user)).thenReturn(List.of());
        when(secureTokenGenerator.generate()).thenReturn("secure-token");
        when(emailService.sendAccountLockedEmail(any(), any(), any())).thenReturn(true);

        service.sendUnlockEmail(user);

        verify(accountUnlockTokenRepository).save(any(AccountUnlockToken.class));
        verify(auditLogService)
                .log(AuditEventType.ACCOUNT_UNLOCK_REQUESTED, user.getId(), user.getEmail());
    }

    @Test
    void resendUnlockEmail_forNonLockedAccount_doesNothing() {
        User user = new User();
        user.setEmail("jane@example.com");
        user.setLockedUntil(null);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resendUnlockEmail(user.getEmail());

        verify(accountUnlockTokenRepository, never()).save(any());
    }

    @Test
    void unlockAccount_withValidToken_clearsLockAndDeletesToken() {
        User user = lockedUser();
        AccountUnlockToken token = new AccountUnlockToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiryDateTime(LocalDateTime.now().plusHours(1));
        when(accountUnlockTokenRepository.findByToken("valid-token"))
                .thenReturn(Optional.of(token));

        service.unlockAccount("valid-token");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
        verify(accountUnlockTokenRepository).delete(token);
        verify(auditLogService).log(AuditEventType.ACCOUNT_UNLOCKED, user.getId(), user.getEmail());
    }

    @Test
    void unlockAccount_withExpiredToken_throwsAndDeletesToken() {
        User user = lockedUser();
        AccountUnlockToken token = new AccountUnlockToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setExpiryDateTime(LocalDateTime.now().minusHours(1));
        when(accountUnlockTokenRepository.findByToken("expired-token"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.unlockAccount("expired-token"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.ACCOUNT_UNLOCK_TOKEN_EXPIRED));

        verify(accountUnlockTokenRepository).delete(token);
    }

    @Test
    void adminUnlock_forLockedUser_clearsLock() {
        User user = lockedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(accountUnlockTokenRepository.findByUser(user)).thenReturn(List.of());

        service.adminUnlock(user.getId());

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(auditLogService)
                .log(AuditEventType.ACCOUNT_UNLOCKED, user.getId(), user.getEmail(), "by admin");
    }

    @Test
    void adminUnlock_forNonLockedUser_throws() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setLockedUntil(null);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.adminUnlock(user.getId()))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.ACCOUNT_NOT_LOCKED));
    }

    @Test
    void adminUnlock_forUnknownUser_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminUnlock(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
