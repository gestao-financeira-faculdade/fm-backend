package com.emanudev.gestaofinanceira.shared.domain.exception;

public class PreconditionFailedException extends DomainException {

    public static final String DEFAULT_ERROR_CODE = "PRECONDITION_FAILED";

    public PreconditionFailedException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }
}
