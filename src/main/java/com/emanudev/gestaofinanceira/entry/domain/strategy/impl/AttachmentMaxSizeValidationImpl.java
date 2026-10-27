package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentValidationStrategy;

public class AttachmentMaxSizeValidationImpl implements AttachmentValidationStrategy {

    @Override
    public void execute(String originalFileName, byte[] fileBytes) {
        if (fileBytes != null && fileBytes.length > FiscalAttachmentValidator.MAX_SIZE_BYTES) {
            throw new InvalidAttachmentFormatException(
                    "Attachment file size (%d bytes) exceeds maximum allowed limit of 10 MB (%d bytes)"
                            .formatted(fileBytes.length, FiscalAttachmentValidator.MAX_SIZE_BYTES)
            );
        }
    }
}
