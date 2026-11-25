package com.henheang.engineeringapi.exception;

import com.henheang.apicore.components.RequestIdFilter;
import com.henheang.apicore.components.common.api.ApiResponse;
import com.henheang.apicore.components.common.api.ApiStatus;
import com.henheang.apicore.components.common.api.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The only place engineering-api turns exceptions into responses: the {@code ApiResponse} envelope
 * with the request id, as security-api's GlobalExceptionHandler does (which this module doesn't
 * depend on). Messages a client sees are ours, never an exception's - those can carry SQL or data.
 */
@Slf4j
@RestControllerAdvice
public class EngineeringExceptionHandler {

    private static final int METHOD_NOT_ALLOWED_CODE = 40500;

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> notFound(NoResourceFoundException ex) {
        return respond(StatusCode.NOT_FOUND.getCode(), "Not found.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> methodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {
        return respond(
                METHOD_NOT_ALLOWED_CODE,
                "This method isn't supported here.",
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> unreadable(HttpMessageNotReadableException ex) {
        return respond(
                StatusCode.BAD_REQUEST.getCode(),
                "The request body is missing or isn't valid JSON.",
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> invalid(MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .orElse("The request contains invalid data.");
        return respond(StatusCode.BAD_REQUEST.getCode(), message, HttpStatus.BAD_REQUEST);
    }

    // Anything unexpected: the type goes to the log (with the request id from
    // MDC), the client gets a generic message.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> unexpected(Exception ex) {
        log.error("Unhandled {}", ex.getClass().getName());
        return respond(
                StatusCode.INTERNAL_SERVER_ERROR.getCode(),
                "Something went wrong.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ApiResponse<Object>> respond(
            int code, String message, HttpStatus status) {
        ApiResponse<Object> body = new ApiResponse<>(new ApiStatus(code, message), message);
        body.getStatusCode().setRequestId(RequestIdFilter.current());
        return new ResponseEntity<>(body, status);
    }
}
