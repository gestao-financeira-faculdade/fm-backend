package com.emanudev.gestaofinanceira.reporting.application;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExportEntriesReportUseCase {

    private final DashboardQueryPort dashboardQueryPort;

    public ExportEntriesReportUseCase(DashboardQueryPort dashboardQueryPort) {
        this.dashboardQueryPort = Objects.requireNonNull(dashboardQueryPort, "dashboardQueryPort must not be null");
    }

    public List<ReportRowItem> fetchRows(ReportExportFilter filter) {
        ReportExportFilter effectiveFilter = filter != null ? filter : ReportExportFilter.empty();
        return dashboardQueryPort.fetchReportEntries(effectiveFilter);
    }
}
