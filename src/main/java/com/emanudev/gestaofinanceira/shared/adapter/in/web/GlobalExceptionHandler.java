package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ConcurrencyConflictException;
import com.emanudev.gestaofinanceira.shared.domain.exception.RateLimitExceededException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ServiceUnavailableException;
import com.emanudev.gestaofinanceira.shared.domain.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApplicationProperties applicationProperties;
    private final Clock clock;

    public GlobalExceptionHandler() {
        this(new ApplicationProperties(), Clock.systemUTC());
    }

    public GlobalExceptionHandler(ApplicationProperties applicationProperties) {
        this(applicationProperties, Clock.systemUTC());
    }

    @Autowired
    public GlobalExceptionHandler(ApplicationProperties applicationProperties, Clock clock) {
        this.applicationProperties = Objects.requireNonNull(applicationProperties, "applicationProperties must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "UNAUTHORIZED")
                .addKeyValue("error_code", ex.getErrorCode())
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
                .log(ex.getMessage());

        return buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                ex.getErrorCode(),
                request
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                ex.getMessage(),
                ex.getErrorCode(),
                request
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "RESOURCE_NOT_FOUND")
                .addKeyValue("error_code", ex.getErrorCode())
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
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
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
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
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detail = unrecognizedProperty(ex)
                .map(property -> "Unknown property '%s' in request body.".formatted(property))
                .orElse("Request body is missing or malformed.");

        log.atWarn()
                .addKeyValue("event", "MALFORMED_REQUEST_BODY")
                .addKeyValue("error_code", "MALFORMED_REQUEST_BODY")
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
                .log(detail);

        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed Request Body",
                detail,
                "MALFORMED_REQUEST_BODY",
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fieldErrors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "One or more parameters failed validation.",
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
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
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
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
                .log("Access denied to resource");

        return buildProblemDetail(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to perform this action.",
                "ACCESS_DENIED",
                request
        );
    }

    private static Optional<String> unrecognizedProperty(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unrecognized) {
                return Optional.ofNullable(unrecognized.getPropertyName());
            }
        }
        return Optional.empty();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Attachment Too Large",
                "The uploaded file exceeds the maximum allowed size.",
                "ATTACHMENT_TOO_LARGE",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Parameter",
                "Parameter '%s' has an invalid value.".formatted(ex.getName()),
                "INVALID_PARAMETER",
                request
        );
        problem.setProperty("fieldErrors", Map.of(ex.getName(), "invalid value"));
        return problem;
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ProblemDetail handleMissingParameter(Exception ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "Missing Parameter", ex.getMessage(), "MISSING_PARAMETER", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipart(MultipartException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed Multipart Request",
                "The multipart request could not be read.",
                "MALFORMED_MULTIPART_REQUEST",
                request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type",
                "Content-Type '%s' is not supported.".formatted(ex.getContentType()),
                "UNSUPPORTED_MEDIA_TYPE",
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildProblemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method Not Allowed",
                "Method %s is not supported for this resource.".formatted(ex.getMethod()),
                "METHOD_NOT_ALLOWED",
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return buildProblemDetail(HttpStatus.NOT_FOUND, "Not Found", "Resource not found.", "NOT_FOUND", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", "DATA_INTEGRITY_VIOLATION")
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
                .log(ex.getMostSpecificCause().getMessage());

        return buildProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                "The request conflicts with existing data (duplicate or invalid reference).",
                "DATA_INTEGRITY_VIOLATION",
                request
        );
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        log.atError()
                .addKeyValue("event", "SERVICE_UNAVAILABLE")
                .addKeyValue("error_code", ex.getErrorCode())
                .log(ex.getMessage());

        return buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), ex.getErrorCode(), request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ProblemDetail handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus effective = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String detail = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : effective.getReasonPhrase();
        return buildProblemDetail(effective, effective.getReasonPhrase(), detail, effective.name(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.atError()
                .addKeyValue("event", "UNEXPECTED_ERROR")
                .addKeyValue("path", request != null ? request.getRequestURI() : "N/A")
                .setCause(ex)
                .log("Unexpected error");

        return buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Use the traceId when reporting it.",
                "INTERNAL_ERROR",
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
        problem.setType(applicationProperties.buildErrorTypeUri(errorCode));
        if (request != null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("error_code", errorCode);
        problem.setProperty("timestamp", Instant.now(clock).toString());
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(CorrelationMdcFilter.MDC_CORRELATION_ID);
        }
        problem.setProperty("traceId", traceId != null ? traceId : "N/A");
        return problem;
    }
}
