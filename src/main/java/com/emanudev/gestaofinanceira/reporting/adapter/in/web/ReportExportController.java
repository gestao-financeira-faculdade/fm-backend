package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.adapter.out.export.CsvStreamingExporter;
import com.emanudev.gestaofinanceira.reporting.adapter.out.export.PdfReportExporter;
import com.emanudev.gestaofinanceira.reporting.application.ExportEntriesReportUseCase;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import com.emanudev.gestaofinanceira.reporting.application.ReportRowItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
public class ReportExportController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final String CSV_FILENAME = "lancamentos-financeiros.csv";
    private static final String PDF_FILENAME = "relatorio-financeiro.pdf";

    private final ExportEntriesReportUseCase exportEntriesReportUseCase;
    private final CsvStreamingExporter csvStreamingExporter;
    private final PdfReportExporter pdfReportExporter;

    public ReportExportController(
            ExportEntriesReportUseCase exportEntriesReportUseCase,
            CsvStreamingExporter csvStreamingExporter,
            PdfReportExporter pdfReportExporter
    ) {
        this.exportEntriesReportUseCase = Objects.requireNonNull(
                exportEntriesReportUseCase,
                "exportEntriesReportUseCase must not be null"
        );
        this.csvStreamingExporter = Objects.requireNonNull(
                csvStreamingExporter,
                "csvStreamingExporter must not be null"
        );
        this.pdfReportExporter = Objects.requireNonNull(
                pdfReportExporter,
                "pdfReportExporter must not be null"
        );
    }

    @GetMapping("/reports/entries/export")
    public ResponseEntity<byte[]> exportEntriesGet(
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) String search
    ) {
        ReportExportFormat targetFormat = ReportExportFormat.fromString(format);
        ReportExportFilter filter = new ReportExportFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        return buildExportResponse(targetFormat, filter);
    }

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportEntriesPost(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) String search,
            @RequestBody(required = false) ExportReportRequest body
    ) {
        String effectiveFormat = firstNonBlank(body != null ? body.format() : null, format, "CSV");
        ReportExportFormat targetFormat = ReportExportFormat.fromString(effectiveFormat);

        ReportExportFilter filter = new ReportExportFilter(
                firstNonBlank(body != null ? body.entryType() : null, entryType, null),
                firstNonBlank(body != null ? body.status() : null, status, null),
                body != null && body.categoryId() != null ? body.categoryId() : categoryId,
                body != null && body.accountId() != null ? body.accountId() : accountId,
                body != null && body.dueDateFrom() != null ? body.dueDateFrom() : dueDateFrom,
                body != null && body.dueDateTo() != null ? body.dueDateTo() : dueDateTo,
                firstNonBlank(body != null ? body.search() : null, search, null)
        );
        return buildExportResponse(targetFormat, filter);
    }

    @GetMapping("/entries/export/csv")
    public ResponseEntity<byte[]> exportEntriesCsvAlias(
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) String search
    ) {
        ReportExportFilter filter = new ReportExportFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        return buildExportResponse(ReportExportFormat.CSV, filter);
    }

    @GetMapping("/entries/export/pdf")
    public ResponseEntity<byte[]> exportEntriesPdfAlias(
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) String search
    ) {
        ReportExportFilter filter = new ReportExportFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        return buildExportResponse(ReportExportFormat.PDF, filter);
    }

    private ResponseEntity<byte[]> buildExportResponse(ReportExportFormat format, ReportExportFilter filter) {
        List<ReportRowItem> rows = exportEntriesReportUseCase.fetchRows(filter);

        if (format == ReportExportFormat.PDF) {
            byte[] pdfBytes = pdfReportExporter.export(rows, filter);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(PDF_FILENAME).build().toString()
                    )
                    .body(pdfBytes);
        }

        byte[] csvBytes = csvStreamingExporter.export(rows);
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(CSV_FILENAME).build().toString()
                )
                .body(csvBytes);
    }

    private static String firstNonBlank(String primary, String secondary, String defaultValue) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return defaultValue;
    }

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
}
