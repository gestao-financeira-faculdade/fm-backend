package com.emanudev.gestaofinanceira.shared.domain.exception;

import java.time.Duration;

public class RateLimitExceededException extends DomainException {

    private final Duration retryAfter;

    public RateLimitExceededException(String errorCode, String message, Duration retryAfter) {
        super(errorCode, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
