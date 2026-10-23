package com.henheang.securityapi.exception;

import com.henheang.commonapi.components.common.api.ApiResponse;
import com.henheang.commonapi.components.common.api.ApiStatus;
import com.henheang.commonapi.components.common.api.ExitCode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(
            BadRequestException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        "This record was modified by someone else - please reload and try again.");

        return new ResponseEntity<>(apiResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(
                        error -> {
                            String fieldName = ((FieldError) error).getField();
                            String errorMessage = error.getDefaultMessage();
                            errors.put(fieldName, errorMessage);
                        });

        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(
            Exception ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(ex.getErrorCode()),
                        ex.getBody() != null ? ex.getBody() : ex.getMessage());

        return new ResponseEntity<>(
                apiResponse, HttpStatus.valueOf(ex.getErrorCode().getHttpCode()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthException(
            AuthException ex, WebRequest request) {
        ApiResponse<Object> apiResponse =
                new ApiResponse<>(
                        new ApiStatus(
                                ExitCode.SYSTEM_ERROR.getCode(),
                                ExitCode.SYSTEM_ERROR.getMessage()),
                        ex.getMessage());

        return new ResponseEntity<>(apiResponse, getHttpStatusForExitCode(ex.getExitCode()));
    }

    private HttpStatus getHttpStatusForExitCode(ExitCode exitCode) {
        int code = exitCode.getCode();

        if (code >= 1000 && code < 1100) {
            return HttpStatus.UNAUTHORIZED; // Authentication errors
        } else if (code >= 1100 && code < 1200) {
            return HttpStatus.BAD_REQUEST; // Registration errors
        } else if (code >= 1200 && code < 1300) {
            return HttpStatus.BAD_REQUEST; // Password reset errors
        } else if (code >= 1300 && code < 1400) {
            return HttpStatus.BAD_REQUEST; // OAuth errors
        } else if (code >= 1400 && code < 1500) {
            return HttpStatus.BAD_REQUEST; // Email verification errors
        } else if (code >= 1500 && code < 1600) {
            return HttpStatus.BAD_REQUEST; // Account unlock errors
        } else if (code >= 5000 && code < 6000) {
            return HttpStatus.INTERNAL_SERVER_ERROR; // System errors
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR; // Default for unknown errors
        }
    }
}
