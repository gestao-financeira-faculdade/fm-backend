package com.emanudev.gestaofinanceira.shared.domain.exception;

public class ResourceNotFoundException extends DomainException {

    public static final String DEFAULT_ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(DEFAULT_ERROR_CODE, "%s not found with id: %s".formatted(resourceName, id));
    }

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
