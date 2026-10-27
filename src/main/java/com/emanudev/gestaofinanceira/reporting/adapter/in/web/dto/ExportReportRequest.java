package com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ExportReportRequest(
        String format,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String type,
        UUID categoryId
) {
}
