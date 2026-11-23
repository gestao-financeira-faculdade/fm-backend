package com.emanudev.gestaofinanceira.reporting.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardQueryPort {

    ExecutiveKpiResponse fetchExecutiveKpis(LocalDate referenceDate);

    UncertaintyConeResponse fetchUncertaintyCone(
            LocalDate referenceDate,
            int historyDays,
            Optional<UUID> simulationId
    );

    List<ReportRowItem> fetchReportEntries(ReportExportFilter filter);
}
