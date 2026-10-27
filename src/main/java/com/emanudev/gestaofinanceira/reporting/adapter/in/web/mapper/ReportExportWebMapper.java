package com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto.ExportReportRequest;
import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class ReportExportWebMapper {

    public ReportExportFormat toFormat(String format) {
        return ReportExportFormat.fromString(format);
    }

    public ReportExportFormat toFormat(String queryFormat, ExportReportRequest body) {
        String effectiveFormat = firstNonBlank(body != null ? body.format() : null, queryFormat, "CSV");
        return ReportExportFormat.fromString(effectiveFormat);
    }

    public ReportExportFilter toFilter(
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search
    ) {
        return new ReportExportFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
    }

    public ReportExportFilter toFilter(
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search,
            ExportReportRequest body
    ) {
        return new ReportExportFilter(
                firstNonBlank(body != null ? body.entryType() : null, entryType, null),
                firstNonBlank(body != null ? body.status() : null, status, null),
                body != null && body.categoryId() != null ? body.categoryId() : categoryId,
                body != null && body.accountId() != null ? body.accountId() : accountId,
                body != null && body.dueDateFrom() != null ? body.dueDateFrom() : dueDateFrom,
                body != null && body.dueDateTo() != null ? body.dueDateTo() : dueDateTo,
                firstNonBlank(body != null ? body.search() : null, search, null)
        );
    }

    public ResponseEntity<byte[]> toResponseEntity(ExportedReport exportedReport) {
        Objects.requireNonNull(exportedReport, "exportedReport must not be null");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportedReport.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(exportedReport.filename()).build().toString()
                )
                .body(exportedReport.content());
    }

    private static String firstNonBlank(String primary, String secondary, String defaultValue) {
        return Stream.of(primary, secondary)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(defaultValue);
    }
}
