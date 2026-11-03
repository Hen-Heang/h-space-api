package com.henheang.securityapi.exception;

import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.StatusCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints (test sources, never shipped) that raise each exception
 * GlobalExceptionHandler maps, so GlobalExceptionHandlerIntegrationTest can check status, envelope
 * and - above all - that no exception detail reaches the client. Under /api/public/** so no token
 * is needed. Every message contains CANARY, which must never appear in a response.
 */
@RestController
@RequestMapping("/api/public/test-probe")
class ExceptionProbeController {

    static final String CANARY = "canary-secret-detail";

    @GetMapping("/ok")
    ApiResponse<String> ok() {
        return ApiResponse.<String>builder().status(StatusCode.SUCCESS).data("ok").build();
    }

    @GetMapping("/uuid/{id}")
    String uuid(@PathVariable UUID id) {
        return id.toString();
    }

    @GetMapping("/param")
    String param(@RequestParam int count) {
        return Integer.toString(count);
    }

    @GetMapping("/constraint")
    String constraint() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            throw new ConstraintViolationException(validator.validate(new Named(" ")));
        }
    }

    @GetMapping("/unique")
    String unique() {
        throw integrity("23505");
    }

    @GetMapping("/foreign-key")
    String foreignKey() {
        throw integrity("23503");
    }

    @GetMapping("/check")
    String check() {
        throw integrity("23514");
    }

    @GetMapping("/boom")
    String boom() {
        throw new IllegalStateException("Internal failure: " + CANARY);
    }

    @GetMapping("/query-timeout")
    String queryTimeout() {
        throw new QueryTimeoutException(
                "ERROR: canceling statement due to statement timeout, " + CANARY);
    }

    @GetMapping("/transaction-timeout")
    String transactionTimeout() {
        throw new TransactionTimedOutException("Transaction timed out: " + CANARY);
    }

    private static DataIntegrityViolationException integrity(String sqlState) {
        return new DataIntegrityViolationException(
                "could not execute statement [Key (name)=(" + CANARY + ") already exists]",
                new SQLException("ERROR: violates constraint, " + CANARY, sqlState));
    }

    record Named(@NotBlank(message = "Name is required") String name) {}
}
