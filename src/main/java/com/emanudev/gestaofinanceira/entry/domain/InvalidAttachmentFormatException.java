package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

public class InvalidAttachmentFormatException extends BusinessRuleViolationException {

    public static final String ERROR_CODE = "INVALID_ATTACHMENT_FORMAT";

    public InvalidAttachmentFormatException(String message) {
        super(ERROR_CODE, message);
    }
}
