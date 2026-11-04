package com.emanudev.gestaofinanceira.reporting.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetUncertaintyConeUseCase {

    public static final int DEFAULT_HISTORY_DAYS = 30;
    public static final int MAX_HISTORY_DAYS = 365;

    private final DashboardQueryPort dashboardQueryPort;
    private final Clock clock;

    @Autowired
    public GetUncertaintyConeUseCase(DashboardQueryPort dashboardQueryPort, Clock clock) {
        this.dashboardQueryPort = Objects.requireNonNull(dashboardQueryPort, "dashboardQueryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public GetUncertaintyConeUseCase(DashboardQueryPort dashboardQueryPort) {
        this(dashboardQueryPort, Clock.systemDefaultZone());
    }

    public UncertaintyConeResponse execute(Integer historyDays, UUID simulationId) {
        return execute(LocalDate.now(clock), historyDays, simulationId);
    }

    public UncertaintyConeResponse execute(LocalDate referenceDate, Integer historyDays, UUID simulationId) {
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now(clock);
        int effectiveDays = (historyDays == null || historyDays <= 0)
                ? DEFAULT_HISTORY_DAYS
                : Math.min(historyDays, MAX_HISTORY_DAYS);
        return dashboardQueryPort.fetchUncertaintyCone(
                effectiveDate,
                effectiveDays,
                Optional.ofNullable(simulationId)
        );
    }

    public UncertaintyConeResponse getUncertaintyCone(Integer historyDays, UUID simulationId) {
        return execute(historyDays, simulationId);
    }
}
