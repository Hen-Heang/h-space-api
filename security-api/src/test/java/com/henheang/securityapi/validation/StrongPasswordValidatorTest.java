package com.henheang.securityapi.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
        ReflectionTestUtils.setField(validator, "minLength", 12);
        ReflectionTestUtils.setField(validator, "requireUppercase", true);
        ReflectionTestUtils.setField(validator, "requireLowercase", true);
        ReflectionTestUtils.setField(validator, "requireDigit", true);
        ReflectionTestUtils.setField(validator, "requireSpecialChar", true);

        context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(builder);
    }

    @Test
    void isValid_forNullOrEmpty_returnsTrue() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
    }

    @Test
    void isValid_forCompliantPassword_returnsTrue() {
        assertThat(validator.isValid("Password123!", context)).isTrue();
    }

    @Test
    void isValid_forTooShortPassword_returnsFalse() {
        assertThat(validator.isValid("Short1!", context)).isFalse();
    }

    @Test
    void isValid_withoutUppercase_returnsFalse() {
        assertThat(validator.isValid("password123!", context)).isFalse();
    }

    @Test
    void isValid_withoutSpecialChar_returnsFalse() {
        assertThat(validator.isValid("Password12345", context)).isFalse();
    }

    @Test
    void isValid_withoutDigit_returnsFalse() {
        assertThat(validator.isValid("Password!!!!", context)).isFalse();
    }
}
