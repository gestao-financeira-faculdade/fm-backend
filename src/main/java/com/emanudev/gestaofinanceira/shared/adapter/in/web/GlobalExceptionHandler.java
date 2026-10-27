package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ConcurrencyConflictException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "RESOURCE_NOT_FOUND")
                .addKeyValue("error_code", ex.getErrorCode())
                .addKeyValue("path", request.getRequestURI())
                .log(ex.getMessage());

        return buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                ex.getErrorCode(),
                request
        );
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "BUSINESS_RULE_VIOLATION")
                .addKeyValue("error_code", ex.getErrorCode())
                .addKeyValue("path", request.getRequestURI())
                .log(ex.getMessage());

        return buildProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Business Rule Violation",
                ex.getMessage(),
                ex.getErrorCode(),
                request
        );
    }

    @ExceptionHandler({
            ConcurrencyConflictException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockingFailureException.class
    })
    public ProblemDetail handleConcurrencyConflict(Exception ex, HttpServletRequest request) {
        String errorCode = ex instanceof ConcurrencyConflictException cce
                ? cce.getErrorCode()
                : ConcurrencyConflictException.DEFAULT_ERROR_CODE;
        String detail = ex instanceof ConcurrencyConflictException
                ? ex.getMessage()
                : "Concurrent modification detected. Please reload the resource and retry.";

        log.atWarn()
                .addKeyValue("event", "CONCURRENCY_CONFLICT")
                .addKeyValue("error_code", errorCode)
                .addKeyValue("path", request.getRequestURI())
                .log(detail);

        return buildProblemDetail(
                HttpStatus.CONFLICT,
                "Concurrency Conflict",
                detail,
                errorCode,
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "One or more request parameters failed validation.",
                "VALIDATION_ERROR",
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "INVALID_ARGUMENT")
                .addKeyValue("error_code", "INVALID_ARGUMENT")
                .addKeyValue("path", request.getRequestURI())
                .log(ex.getMessage());

        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                "INVALID_ARGUMENT",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "ACCESS_DENIED")
                .addKeyValue("error_code", "ACCESS_DENIED")
                .addKeyValue("path", request.getRequestURI())
                .log("Access denied to resource");

        return buildProblemDetail(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to perform this action.",
                "ACCESS_DENIED",
                request
        );
    }

    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            String errorCode,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (request != null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(CorrelationMdcFilter.MDC_CORRELATION_ID);
        }
        problem.setProperty("traceId", traceId != null ? traceId : "N/A");
        return problem;
    }
}
