package com.henheang.financeapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.BusinessException;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for the rules shared by the lookup services. */
class LookupRulesTest {

    @Test
    void duplicateNameIsAConflictWithLuyrasMessage() {
        BusinessException ex = LookupRules.duplicateName("category", "Test Food");

        assertThat(ex.getErrorCode()).isEqualTo(StatusCode.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("You already have a category named \"Test Food\".");
    }

    @Test
    void requiredRejectsAnExplicitNull() {
        assertThat(LookupRules.required(Optional.of("x"), "name")).isEqualTo("x");
        assertThatThrownBy(() -> LookupRules.required(Optional.empty(), "name"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("name can't be null");
    }

    @Test
    void recognisesOnlyTheNamedUniqueViolation() {
        DataIntegrityViolationException nameClash =
                new DataIntegrityViolationException(
                        "duplicate",
                        new ConstraintViolationException(
                                "duplicate key",
                                new SQLException("duplicate key", "23505"),
                                "uq_finance_categories_user_name"));
        DataIntegrityViolationException other =
                new DataIntegrityViolationException(
                        "check",
                        new ConstraintViolationException(
                                "check", new SQLException("check", "23514"), "some_check"));

        assertThat(LookupRules.isViolationOf(nameClash, "uq_finance_categories_user_name"))
                .isTrue();
        assertThat(LookupRules.isViolationOf(other, "uq_finance_categories_user_name")).isFalse();
        assertThat(
                        LookupRules.isViolationOf(
                                new DataIntegrityViolationException("no cause"),
                                "uq_finance_categories_user_name"))
                .isFalse();
    }

    // A Postgres server with a non-English lc_messages (this project's local
    // database reports in Korean) defeats Hibernate's message parsing, so the
    // constraint name is null and only the SQLState is reliable.
    @Test
    void fallsBackToTheSqlStateWhenTheConstraintNameIsUnknown() {
        DataIntegrityViolationException localizedUnique =
                new DataIntegrityViolationException(
                        "duplicate",
                        new ConstraintViolationException(
                                "중복된 키 값", new SQLException("중복된 키 값", "23505"), null));
        DataIntegrityViolationException localizedCheck =
                new DataIntegrityViolationException(
                        "check",
                        new ConstraintViolationException(
                                "체크 제약 조건", new SQLException("체크 제약 조건", "23514"), null));

        assertThat(LookupRules.isViolationOf(localizedUnique, "uq_finance_categories_user_name"))
                .isTrue();
        assertThat(LookupRules.isViolationOf(localizedCheck, "uq_finance_categories_user_name"))
                .isFalse();
    }
}
