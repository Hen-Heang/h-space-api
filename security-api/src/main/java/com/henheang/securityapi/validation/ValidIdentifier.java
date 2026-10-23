package com.henheang.securityapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidIdentifierValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIdentifier {

    String message() default "Either email or phone number must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
