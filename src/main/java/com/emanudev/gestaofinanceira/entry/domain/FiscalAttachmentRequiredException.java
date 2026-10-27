package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

public class FiscalAttachmentRequiredException extends BusinessRuleViolationException {

    public static final String ERROR_CODE = "FISCAL_ATTACHMENT_REQUIRED";

    public FiscalAttachmentRequiredException(String message) {
        super(ERROR_CODE, message);
    }
}
