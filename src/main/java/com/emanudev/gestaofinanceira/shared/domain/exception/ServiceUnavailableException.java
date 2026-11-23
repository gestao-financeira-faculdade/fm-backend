package com.emanudev.gestaofinanceira.shared.domain.exception;

public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ServiceUnavailableException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
