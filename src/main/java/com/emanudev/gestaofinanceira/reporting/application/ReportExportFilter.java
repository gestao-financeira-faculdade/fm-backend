package com.emanudev.gestaofinanceira.reporting.application;

import java.time.LocalDate;
import java.util.UUID;

public record ReportExportFilter(
        String entryType,
        String status,
        UUID categoryId,
        UUID accountId,
        LocalDate dueDateFrom,
        LocalDate dueDateTo,
        String search
) {

    public static ReportExportFilter empty() {
        return new ReportExportFilter(null, null, null, null, null, null, null);
    }
}
