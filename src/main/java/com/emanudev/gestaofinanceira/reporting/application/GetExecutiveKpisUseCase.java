package com.emanudev.gestaofinanceira.reporting.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetExecutiveKpisUseCase {

    private final DashboardQueryPort dashboardQueryPort;
    private final Clock clock;

    @Autowired
    public GetExecutiveKpisUseCase(DashboardQueryPort dashboardQueryPort, Clock clock) {
        this.dashboardQueryPort = Objects.requireNonNull(dashboardQueryPort, "dashboardQueryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public GetExecutiveKpisUseCase(DashboardQueryPort dashboardQueryPort) {
        this(dashboardQueryPort, Clock.systemDefaultZone());
    }

    public ExecutiveKpiResponse execute() {
        return execute(LocalDate.now(clock));
    }

    public ExecutiveKpiResponse execute(LocalDate referenceDate) {
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now(clock);
        return dashboardQueryPort.fetchExecutiveKpis(effectiveDate);
    }

    public ExecutiveKpiResponse getExecutiveKpis() {
        return execute();
    }

    public ExecutiveKpiResponse getExecutiveKpis(LocalDate referenceDate) {
        return execute(referenceDate);
    }
}
