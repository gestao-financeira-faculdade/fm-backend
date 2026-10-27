package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.reporting.application.strategy.ReportExportStrategy;
import com.emanudev.gestaofinanceira.reporting.application.strategy.factory.ReportExportStrategyFactory;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingUseCasesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private DashboardQueryPort dashboardQueryPort;

    @Test
    void shouldUseClockDateWhenReferenceDateIsMissingForExecutiveKpis() {
        GetExecutiveKpisUseCase useCase = new GetExecutiveKpisUseCase(dashboardQueryPort, FIXED_CLOCK);

        useCase.execute();

        verify(dashboardQueryPort).fetchExecutiveKpis(TODAY);
    }

    @Test
    void shouldApplyDefaultHistoryDaysWhenParameterIsNullOrNonPositive() {
        GetUncertaintyConeUseCase useCase = new GetUncertaintyConeUseCase(dashboardQueryPort, FIXED_CLOCK);

        useCase.execute(null, null);
        useCase.execute(TODAY, 0, null);

        verify(dashboardQueryPort, org.mockito.Mockito.times(2))
                .fetchUncertaintyCone(TODAY, GetUncertaintyConeUseCase.DEFAULT_HISTORY_DAYS, Optional.empty());
    }

    @Test
    void shouldClampHistoryDaysToMaximumAllowed() {
        GetUncertaintyConeUseCase useCase = new GetUncertaintyConeUseCase(dashboardQueryPort, FIXED_CLOCK);
        UUID simulationId = UUID.randomUUID();

        useCase.execute(TODAY, 5_000, simulationId);

        verify(dashboardQueryPort).fetchUncertaintyCone(
                TODAY, GetUncertaintyConeUseCase.MAX_HISTORY_DAYS, Optional.of(simulationId)
        );
    }

    @Test
    void shouldDelegateExportToStrategySelectedByFormat() {
        ExportedReport expected = new ExportedReport(new byte[]{1, 2, 3}, "text/csv", "report.csv");
        ReportExportStrategy csvStrategy = new FixedStrategy(ReportExportFormat.CSV, expected);
        ExportEntriesReportUseCase useCase = new ExportEntriesReportUseCase(
                dashboardQueryPort, new ReportExportStrategyFactory(Set.of(csvStrategy))
        );
        when(dashboardQueryPort.fetchReportEntries(ReportExportFilter.empty())).thenReturn(List.of());

        ExportedReport result = useCase.export(ReportExportFormat.CSV, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void shouldRejectFormatWithoutRegisteredStrategy() {
        ExportEntriesReportUseCase useCase = new ExportEntriesReportUseCase(
                dashboardQueryPort, new ReportExportStrategyFactory(Set.of())
        );
        when(dashboardQueryPort.fetchReportEntries(ReportExportFilter.empty())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.export(ReportExportFormat.PDF, ReportExportFilter.empty()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Unsupported export format");
    }

    private record FixedStrategy(ReportExportFormat format, ExportedReport report) implements ReportExportStrategy {

        @Override
        public ExportedReport exportReport(List<ReportRowItem> rows, ReportExportFilter filter) {
            return report;
        }

        @Override
        public ReportExportFormat getFormat() {
            return format;
        }
    }
}
