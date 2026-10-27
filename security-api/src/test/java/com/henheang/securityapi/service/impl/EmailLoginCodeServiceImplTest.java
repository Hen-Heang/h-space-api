package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.securityapi.domain.EmailLoginCode;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.repository.EmailLoginCodeRepository;
import com.henheang.securityapi.service.EmailService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailLoginCodeServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    private EmailLoginCodeRepository repository;
    private EmailService emailService;
    private EmailLoginCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailLoginCodeRepository.class);
        emailService = mock(EmailService.class);
        service =
                new EmailLoginCodeServiceImpl(
                        repository,
                        emailService,
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(60),
                        3,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    // Sends a code and returns it as the user would read it from the email.
    private String sendAndCaptureCode(String email) {
        when(emailService.sendLoginCodeEmail(anyString(), anyString(), anyLong())).thenReturn(true);
        service.sendCode(email);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService)
                .sendLoginCodeEmail(eq(email.trim().toLowerCase()), code.capture(), eq(10L));
        return code.getValue();
    }

    private EmailLoginCode savedCode() {
        ArgumentCaptor<EmailLoginCode> saved = ArgumentCaptor.forClass(EmailLoginCode.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void sendCode_storesOnlyAHashOfASixDigitCode() {
        String code = sendAndCaptureCode(" User@Example.com");

        EmailLoginCode stored = savedCode();
        assertThat(code).matches("\\d{6}");
        assertThat(stored.getEmail()).isEqualTo("user@example.com");
        assertThat(stored.getCodeHash()).hasSize(64).doesNotContain(code);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void sendCode_withinCooldown_sendsNothing() {
        EmailLoginCode recent = new EmailLoginCode();
        recent.setExpiresAt(NOW.plusSeconds(500));
        recent.setCreatedAt(NOW.minusSeconds(10));
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(recent));

        service.sendCode("user@example.com");

        verify(emailService, never()).sendLoginCodeEmail(anyString(), anyString(), anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void sendCode_afterCooldown_replacesTheOldCode() {
        EmailLoginCode old = new EmailLoginCode();
        old.setExpiresAt(NOW.plusSeconds(500));
        old.setCreatedAt(NOW.minusSeconds(120));
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(old));

        sendAndCaptureCode("user@example.com");

        verify(repository).delete(old);
        verify(repository).save(any(EmailLoginCode.class));
    }

    @Test
    void sendCode_whenEmailFails_throws() {
        when(emailService.sendLoginCodeEmail(anyString(), anyString(), anyLong()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendCode("user@example.com"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void consumeCode_withTheRightCode_succeedsOnce() {
        String code = sendAndCaptureCode("user@example.com");
        EmailLoginCode stored = savedCode();
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));

        assertThat(service.consumeCode("USER@example.com", code)).isTrue();
        verify(repository).delete(stored);
    }

    @Test
    void consumeCode_withAWrongCode_countsTheAttempt() {
        String code = sendAndCaptureCode("user@example.com");
        EmailLoginCode stored = savedCode();
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThat(service.consumeCode("user@example.com", wrong)).isFalse();
        assertThat(stored.getAttempts()).isEqualTo(1);
        verify(repository, never()).delete(stored);
    }

    @Test
    void consumeCode_afterMaxWrongAttempts_discardsTheCode() {
        String code = sendAndCaptureCode("user@example.com");
        EmailLoginCode stored = savedCode();
        stored.setAttempts(2);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThat(service.consumeCode("user@example.com", wrong)).isFalse();
        verify(repository).delete(stored);
    }

    @Test
    void consumeCode_whenExpired_failsEvenWithTheRightCode() {
        String code = sendAndCaptureCode("user@example.com");
        EmailLoginCode stored = savedCode();
        stored.setExpiresAt(NOW);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(stored));

        assertThat(service.consumeCode("user@example.com", code)).isFalse();
        verify(repository).delete(stored);
    }

    @Test
    void consumeCode_withNoOutstandingCode_fails() {
        assertThat(service.consumeCode("user@example.com", "123456")).isFalse();
    }
}
