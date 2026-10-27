package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentValidationStrategy;

public class AttachmentFileNameValidationImpl implements AttachmentValidationStrategy {

    @Override
    public void execute(String originalFileName, byte[] fileBytes) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidAttachmentFormatException("Attachment original file name must not be blank");
        }
    }
}
