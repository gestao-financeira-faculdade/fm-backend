package com.emanudev.gestaofinanceira.reporting.application;

import java.util.Objects;

public record ExportedReport(
        byte[] content,
        String contentType,
        String filename
) {
    public ExportedReport {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
    }
}
