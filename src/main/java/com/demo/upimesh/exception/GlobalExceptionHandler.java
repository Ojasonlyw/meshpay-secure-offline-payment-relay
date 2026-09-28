package com.demo.upimesh.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> domain(DomainException e, HttpServletRequest request) {
        return response(e.getStatus(), e.getErrorCode(), e.getPublicMessage(), e, request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> validation(Exception e, HttpServletRequest request) {
        return response(400, "VALIDATION_FAILED", "Request validation failed.", e, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        return response(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.", e, request);
    }

    private ResponseEntity<ApiErrorResponse> response(int status, String code, String message,
                                                       Exception e, HttpServletRequest request) {
        String traceId = TraceIdFilter.traceId(request);
        log.error("Request failed traceId={} path={} errorCode={}", traceId, request.getRequestURI(), code, e);
        return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(
                new ApiErrorResponse(Instant.now(), status, code, message, request.getRequestURI(), traceId));
    }
}

