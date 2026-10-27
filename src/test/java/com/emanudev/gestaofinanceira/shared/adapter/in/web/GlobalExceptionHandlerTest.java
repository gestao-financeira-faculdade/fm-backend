package com.emanudev.gestaofinanceira.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.emanudev.gestaofinanceira.shared.config.ApplicationProperties;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import com.emanudev.gestaofinanceira.shared.domain.exception.ConcurrencyConflictException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        ReflectionTestUtils.setField(properties, "errorBaseUri", "http://localhost:8080/errors");
        handler = new GlobalExceptionHandler(properties);
        request = new MockHttpServletRequest("POST", "/api/v1/entries");
        MDC.put("traceId", "trace-abc-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should map ResourceNotFoundException to HTTP 404 ProblemDetail")
    void shouldMapResourceNotFoundTo404() {
        UUID id = UUID.randomUUID();
        ResourceNotFoundException ex = new ResourceNotFoundException("FinancialEntry", id);

        ProblemDetail problem = handler.handleResourceNotFound(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getDetail()).contains(id.toString());
        assertThat(problem.getProperties()).containsEntry("errorCode", "RESOURCE_NOT_FOUND");
        assertThat(problem.getProperties()).containsEntry("traceId", "trace-abc-123");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("Should map BusinessRuleViolationException to HTTP 422 ProblemDetail")
    void shouldMapBusinessRuleViolationTo422() {
        BusinessRuleViolationException ex = new BusinessRuleViolationException(
                "MISSING_FISCAL_ATTACHMENT",
                "Entries above BRL 5000.00 require a fiscal attachment"
        );

        ProblemDetail problem = handler.handleBusinessRuleViolation(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "MISSING_FISCAL_ATTACHMENT");
        assertThat(problem.getProperties()).containsEntry("traceId", "trace-abc-123");
    }

    @Test
    @DisplayName("Should map ConcurrencyConflictException to HTTP 409 ProblemDetail")
    void shouldMapConcurrencyConflictTo409() {
        ConcurrencyConflictException ex = new ConcurrencyConflictException("Entry already settled concurrently");

        ProblemDetail problem = handler.handleConcurrencyConflict(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "CONCURRENCY_CONFLICT");
        assertThat(problem.getProperties()).containsEntry("traceId", "trace-abc-123");
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to HTTP 400 ProblemDetail and AccessDeniedException to HTTP 403")
    void shouldMapBadRequestAndForbidden() {
        ProblemDetail badRequest = handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid date range"),
                request
        );
        ProblemDetail forbidden = handler.handleAccessDenied(
                new AccessDeniedException("Denied"),
                request
        );

        assertThat(badRequest.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(badRequest.getProperties()).containsEntry("errorCode", "INVALID_ARGUMENT");

        assertThat(forbidden.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(forbidden.getProperties()).containsEntry("errorCode", "ACCESS_DENIED");
    }
}
