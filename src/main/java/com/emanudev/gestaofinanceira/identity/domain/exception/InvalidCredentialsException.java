package com.emanudev.gestaofinanceira.identity.domain.exception;

import com.emanudev.gestaofinanceira.shared.domain.exception.DomainException;

public class InvalidCredentialsException extends DomainException {

    public static final String DEFAULT_ERROR_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public InvalidCredentialsException(String errorCode, String message) {
        super(errorCode, message);
    }
}
