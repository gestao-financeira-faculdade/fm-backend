package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

public class FiscalAttachmentRequiredException extends BusinessRuleViolationException {

    public static final String ERROR_CODE = "MISSING_FISCAL_ATTACHMENT";

    public FiscalAttachmentRequiredException(String message) {
        super(ERROR_CODE, message);
    }
}
