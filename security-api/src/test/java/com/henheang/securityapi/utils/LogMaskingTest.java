package com.henheang.securityapi.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void email_keepsOnlyTheFirstCharacterAndTheDomain() {
        assertThat(LogMasking.email("jane.doe@example.com")).isEqualTo("j***@example.com");
    }

    @Test
    void email_missingValue_isMarkedNotEchoed() {
        assertThat(LogMasking.email(null)).isEqualTo("<none>");
        assertThat(LogMasking.email("  ")).isEqualTo("<none>");
    }

    @Test
    void email_malformed_isMaskedLikeAUsername() {
        assertThat(LogMasking.email("@example.com")).isEqualTo("@***");
        assertThat(LogMasking.email("jane@")).isEqualTo("j***");
    }

    @Test
    void identifier_username_keepsOnlyTheFirstCharacter() {
        assertThat(LogMasking.identifier("janedoe")).isEqualTo("j***");
    }

    @Test
    void identifier_email_isMaskedAsAnEmail() {
        assertThat(LogMasking.identifier(" jane@example.com ")).isEqualTo("j***@example.com");
    }
}
