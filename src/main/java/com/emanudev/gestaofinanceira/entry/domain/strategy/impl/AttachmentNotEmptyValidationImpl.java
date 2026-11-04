package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentValidationStrategy;

public class AttachmentNotEmptyValidationImpl implements AttachmentValidationStrategy {

    @Override
    public void execute(String originalFileName, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new InvalidAttachmentFormatException("Attachment file must not be empty (0 bytes)");
        }
    }
}
