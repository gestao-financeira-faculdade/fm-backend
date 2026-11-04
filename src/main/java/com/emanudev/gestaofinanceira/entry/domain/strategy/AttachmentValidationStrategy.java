package com.emanudev.gestaofinanceira.entry.domain.strategy;

public interface AttachmentValidationStrategy {

    void execute(String originalFileName, byte[] fileBytes);
}
