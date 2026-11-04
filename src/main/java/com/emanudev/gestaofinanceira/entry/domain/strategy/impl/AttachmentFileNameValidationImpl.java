package com.emanudev.gestaofinanceira.entry.domain.strategy.impl;

import com.emanudev.gestaofinanceira.entry.domain.InvalidAttachmentFormatException;
import com.emanudev.gestaofinanceira.entry.domain.strategy.AttachmentValidationStrategy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AttachmentFileNameValidationImpl implements AttachmentValidationStrategy {

    @Override
    public void execute(String originalFileName, byte[] fileBytes) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidAttachmentFormatException("Attachment original file name must not be blank");
        }
    }
}
