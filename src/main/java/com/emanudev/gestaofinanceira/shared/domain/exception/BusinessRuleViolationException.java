package com.emanudev.gestaofinanceira.shared.domain.exception;

public class BusinessRuleViolationException extends DomainException {

    public static final String DEFAULT_ERROR_CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }
}
