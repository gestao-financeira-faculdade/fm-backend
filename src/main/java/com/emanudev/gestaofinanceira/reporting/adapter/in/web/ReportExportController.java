package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto.ExportReportRequest;
import com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper.ReportExportWebMapper;
import com.emanudev.gestaofinanceira.reporting.application.ExportEntriesReportUseCase;
import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.application.ReportExportFormat;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
public class ReportExportController implements ReportExportControllerOpenApi {

    private final ExportEntriesReportUseCase exportEntriesReportUseCase;
    private final ReportExportWebMapper reportExportWebMapper;

    public ReportExportController(
            ExportEntriesReportUseCase exportEntriesReportUseCase,
            ReportExportWebMapper reportExportWebMapper
    ) {
        this.exportEntriesReportUseCase = Objects.requireNonNull(
                exportEntriesReportUseCase,
                "exportEntriesReportUseCase must not be null"
        );
        this.reportExportWebMapper = Objects.requireNonNull(
                reportExportWebMapper,
                "reportExportWebMapper must not be null"
        );
    }

    @Override
    public ResponseEntity<byte[]> exportEntriesGet(
            String format,
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search
    ) {
        ReportExportFormat targetFormat = reportExportWebMapper.toFormat(format);
        ReportExportFilter filter = reportExportWebMapper.toFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        ExportedReport exportedReport = exportEntriesReportUseCase.export(targetFormat, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }

    @Override
    public ResponseEntity<byte[]> exportEntriesPost(
            String format,
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search,
            ExportReportRequest body
    ) {
        ReportExportFormat targetFormat = reportExportWebMapper.toFormat(format, body);
        ReportExportFilter filter = reportExportWebMapper.toFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search,
                body
        );
        ExportedReport exportedReport = exportEntriesReportUseCase.export(targetFormat, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }

    @Override
    public ResponseEntity<byte[]> exportEntriesCsvAlias(
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search
    ) {
        ReportExportFilter filter = reportExportWebMapper.toFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        ExportedReport exportedReport = exportEntriesReportUseCase.export(ReportExportFormat.CSV, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }

    @Override
    public ResponseEntity<byte[]> exportEntriesPdfAlias(
            String entryType,
            String status,
            UUID categoryId,
            UUID accountId,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String search
    ) {
        ReportExportFilter filter = reportExportWebMapper.toFilter(
                entryType,
                status,
                categoryId,
                accountId,
                dueDateFrom,
                dueDateTo,
                search
        );
        ExportedReport exportedReport = exportEntriesReportUseCase.export(ReportExportFormat.PDF, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }
}
