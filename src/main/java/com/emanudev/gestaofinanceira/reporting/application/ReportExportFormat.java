package com.emanudev.gestaofinanceira.reporting.application;

import java.util.Locale;

public enum ReportExportFormat {
    CSV,
    PDF;

    public static ReportExportFormat fromString(String format) {
        if (format == null || format.isBlank()) {
            return CSV;
        }
        String normalized = format.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CSV" -> CSV;
            case "PDF" -> PDF;
            default -> throw new IllegalArgumentException("Unsupported report export format: " + format);
        };
    }
}
