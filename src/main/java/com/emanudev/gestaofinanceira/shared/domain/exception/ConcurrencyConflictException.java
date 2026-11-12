package com.emanudev.gestaofinanceira.shared.domain.exception;

public class ConcurrencyConflictException extends DomainException {

    public static final String DEFAULT_ERROR_CODE = "CONCURRENCY_CONFLICT";

    public ConcurrencyConflictException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ConcurrencyConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConcurrencyConflictException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
