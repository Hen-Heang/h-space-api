package com.henheang.financeapi.service.impl;

import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.BusinessException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

// Rules shared by the category and payment-method services.
final class LookupRules {

    private LookupRules() {}

    static BusinessException duplicateName(String noun, String existingName) {
        // Same wording as Luyra's finance-lookup-service, so the UI's toast is
        // unchanged.
        return new BusinessException(
                StatusCode.CONFLICT,
                "You already have a " + noun + " named \"" + existingName + "\".");
    }

    static <T> T required(Optional<T> value, String field) {
        return value.orElseThrow(() -> new BadRequestException(field + " can't be null"));
    }

    private static final String UNIQUE_VIOLATION = "23505";

    // The pre-check in the services catches almost every duplicate; two
    // concurrent requests can still both pass it, and then the unique index
    // rejects the second. Turn that one violation into the same 409 and let
    // anything else propagate.
    //
    // Hibernate reads the constraint name out of the server's message text,
    // so on a non-English Postgres locale it is null. When it is, fall back to
    // the SQLState: on these tables the only other unique keys (the primary
    // key and (id, user_id)) contain a fresh server-generated UUID, so a
    // unique violation can only be the name index.
    static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null) {
                    return constraintName.equalsIgnoreCase(name);
                }
                return UNIQUE_VIOLATION.equals(violation.getSQLState());
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
