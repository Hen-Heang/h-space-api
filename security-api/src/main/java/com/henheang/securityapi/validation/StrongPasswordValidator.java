package com.henheang.securityapi.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

// Instantiated via SpringConstraintValidatorFactory (auto-configured by
// spring-boot-starter-validation), so @Value works here same as in any bean -
// lets the policy be tuned per-environment without a code change.
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Value("${app.password-policy.min-length:12}")
    private int minLength;

    @Value("${app.password-policy.require-uppercase:true}")
    private boolean requireUppercase;

    @Value("${app.password-policy.require-lowercase:true}")
    private boolean requireLowercase;

    @Value("${app.password-policy.require-digit:true}")
    private boolean requireDigit;

    @Value("${app.password-policy.require-special-char:true}")
    private boolean requireSpecialChar;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isEmpty()) {
            // @NotBlank on the field already reports this - avoid a duplicate message.
            return true;
        }

        StringBuilder violations = new StringBuilder();
        if (password.length() < minLength) {
            violations.append("at least ").append(minLength).append(" characters, ");
        }
        if (requireUppercase && password.chars().noneMatch(Character::isUpperCase)) {
            violations.append("an uppercase letter, ");
        }
        if (requireLowercase && password.chars().noneMatch(Character::isLowerCase)) {
            violations.append("a lowercase letter, ");
        }
        if (requireDigit && password.chars().noneMatch(Character::isDigit)) {
            violations.append("a digit, ");
        }
        if (requireSpecialChar && password.chars().allMatch(Character::isLetterOrDigit)) {
            violations.append("a special character, ");
        }

        if (violations.isEmpty()) {
            return true;
        }

        String requirements = violations.substring(0, violations.length() - 2);
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Password must contain " + requirements)
                .addConstraintViolation();
        return false;
    }
}
