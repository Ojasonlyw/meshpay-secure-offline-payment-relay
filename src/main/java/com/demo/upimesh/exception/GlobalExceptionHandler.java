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
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> framework(Exception e, HttpServletRequest request) {
        ErrorResponse framework = (ErrorResponse) e;
        int status = framework.getStatusCode().value();
        String code = switch (status) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> throw new IllegalStateException("Unexpected framework status");
        };
        String message = switch (status) {
            case 404 -> "Resource not found.";
            case 405 -> "Request method not supported.";
            case 415 -> "Request media type not supported.";
            default -> throw new IllegalStateException("Unexpected framework status");
        };
        var result = response(status, code, message, e, request);
        return ResponseEntity.status(status).headers(framework.getHeaders())
                .headers(result.getHeaders()).body(result.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        return response(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.", e, request);
    }

    private ResponseEntity<ApiErrorResponse> response(int status, String code, String message,
                                                       Exception e, HttpServletRequest request) {
        String traceId = TraceIdFilter.traceId(request);
        log.error("Request failed traceId={} errorCode={} exceptionType={}", traceId, code, e.getClass().getSimpleName());
        return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(
                new ApiErrorResponse(Instant.now(), status, code, message, request.getRequestURI(), traceId));
    }
}

