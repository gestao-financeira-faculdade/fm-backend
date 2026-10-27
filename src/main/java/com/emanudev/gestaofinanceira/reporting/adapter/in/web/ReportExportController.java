package com.emanudev.gestaofinanceira.reporting.adapter.in.web;

import com.emanudev.gestaofinanceira.reporting.adapter.in.web.dto.ExportReportRequest;
import com.emanudev.gestaofinanceira.reporting.adapter.in.web.mapper.ReportExportWebMapper;
import com.emanudev.gestaofinanceira.reporting.application.ExportEntriesReportUseCase;
import com.emanudev.gestaofinanceira.reporting.application.ExportedReport;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFilter;
import com.emanudev.gestaofinanceira.reporting.domain.model.ReportExportFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST', 'AUDITOR')")
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
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String type,
            UUID categoryId
    ) {
        ReportExportFormat exportFormat = reportExportWebMapper.toFormat(format);
        ReportExportFilter filter = reportExportWebMapper.toFilter(startDate, endDate, status, type, categoryId);
        ExportedReport exportedReport = exportEntriesReportUseCase.export(exportFormat, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }

    @Override
    public ResponseEntity<byte[]> exportEntriesPost(
            String queryFormat,
            LocalDate queryStartDate,
            LocalDate queryEndDate,
            String queryStatus,
            String queryType,
            UUID queryCategoryId,
            ExportReportRequest body
    ) {
        ReportExportFormat exportFormat = reportExportWebMapper.toFormat(queryFormat, body);
        ReportExportFilter filter = reportExportWebMapper.toFilter(
                queryStartDate,
                queryEndDate,
                queryStatus,
                queryType,
                queryCategoryId,
                body
        );
        ExportedReport exportedReport = exportEntriesReportUseCase.export(exportFormat, filter);
        return reportExportWebMapper.toResponseEntity(exportedReport);
    }
}
