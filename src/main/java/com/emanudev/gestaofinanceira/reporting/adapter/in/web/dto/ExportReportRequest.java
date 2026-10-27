package com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ExportReportRequest(
        String format,
        String entryType,
        String status,
        UUID categoryId,
        UUID accountId,
        LocalDate dueDateFrom,
        LocalDate dueDateTo,
        String search
) {
}
