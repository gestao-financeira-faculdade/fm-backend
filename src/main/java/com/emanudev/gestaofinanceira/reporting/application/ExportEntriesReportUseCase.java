package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.reporting.application.strategy.factory.ReportExportStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ExportEntriesReportUseCase {

    private final DashboardQueryPort dashboardQueryPort;
    private final ReportExportStrategyFactory reportExportStrategyFactory;

    public ExportEntriesReportUseCase(
            DashboardQueryPort dashboardQueryPort,
            ReportExportStrategyFactory reportExportStrategyFactory
    ) {
        this.dashboardQueryPort = Objects.requireNonNull(dashboardQueryPort, "dashboardQueryPort must not be null");
        this.reportExportStrategyFactory = Objects.requireNonNull(
                reportExportStrategyFactory,
                "reportExportStrategyFactory must not be null"
        );
    }

    public List<ReportRowItem> fetchRows(ReportExportFilter filter) {
        ReportExportFilter effectiveFilter = filter != null ? filter : ReportExportFilter.empty();
        return dashboardQueryPort.fetchReportEntries(effectiveFilter);
    }

    public ExportedReport export(ReportExportFormat format, ReportExportFilter filter) {
        ReportExportFilter effectiveFilter = filter != null ? filter : ReportExportFilter.empty();
        List<ReportRowItem> rows = fetchRows(effectiveFilter);
        return reportExportStrategyFactory.getStrategy(format).exportReport(rows, effectiveFilter);
    }
}
