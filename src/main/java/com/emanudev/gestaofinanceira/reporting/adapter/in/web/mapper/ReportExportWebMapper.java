package com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper;

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
