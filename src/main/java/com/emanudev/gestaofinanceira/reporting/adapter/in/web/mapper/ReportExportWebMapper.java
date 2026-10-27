package com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto.ExportReportRequest;
import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReportExportWebMapper {

    public ReportExportFormat toFormat(String rawFormat) {
        return ReportExportFormat.fromString(rawFormat);
    }

    public ReportExportFormat toFormat(String queryFormat, ExportReportRequest body) {
        String effectiveFormat = firstNonBlank(queryFormat, body != null ? body.format() : null);
        return ReportExportFormat.fromString(effectiveFormat);
    }

    public ReportExportFilter toFilter(
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String type,
            UUID categoryId
    ) {
        return new ReportExportFilter(startDate, endDate, status, type, categoryId);
    }

    public ReportExportFilter toFilter(
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String type,
            UUID categoryId,
            ExportReportRequest body
    ) {
        LocalDate effectiveStart = startDate != null ? startDate : (body != null ? body.startDate() : null);
        LocalDate effectiveEnd = endDate != null ? endDate : (body != null ? body.endDate() : null);
        String effectiveStatus = firstNonBlank(status, body != null ? body.status() : null);
        String effectiveType = firstNonBlank(type, body != null ? body.type() : null);
        UUID effectiveCategory = categoryId != null ? categoryId : (body != null ? body.categoryId() : null);

        return new ReportExportFilter(effectiveStart, effectiveEnd, effectiveStatus, effectiveType, effectiveCategory);
    }

    public ResponseEntity<byte[]> toResponseEntity(ExportedReport exportedReport) {
        Objects.requireNonNull(exportedReport, "exportedReport must not be null");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(exportedReport.contentType()));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(exportedReport.filename())
                        .build()
        );
        headers.setContentLength(exportedReport.content().length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportedReport.content());
    }

    private String firstNonBlank(String primary, String fallback) {
        return Optional.ofNullable(primary)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }
}
