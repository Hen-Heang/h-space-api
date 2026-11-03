package com.henheang.financeapi.service.impl;

import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.financeapi.domain.LookupUsage;
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

    // Luyra's wording: "Food" is still used by 12 transactions, 1 recurring
    // rule. Reassign or delete those first.
    static BusinessException inUse(String name, LookupUsage usage) {
        return new BusinessException(
                StatusCode.CONFLICT,
                "\""
                        + name
                        + "\" is still used by "
                        + usage.describe()
                        + ". Reassign or delete those first.");
    }

    // A row added between the usage count and the delete is caught by the
    // composite foreign key (NO ACTION): the same 409, without counts.
    static BusinessException inUse(String name) {
        return new BusinessException(
                StatusCode.CONFLICT,
                "\"" + name + "\" is still in use. Reassign or delete those first.");
    }

    static boolean isForeignKeyViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return FOREIGN_KEY_VIOLATION.equals(violation.getSQLState());
            }
            if (cause instanceof java.sql.SQLException sql) {
                return FOREIGN_KEY_VIOLATION.equals(sql.getSQLState());
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    static <T> T required(Optional<T> value, String field) {
        return value.orElseThrow(() -> new BadRequestException(field + " can't be null"));
    }

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

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
