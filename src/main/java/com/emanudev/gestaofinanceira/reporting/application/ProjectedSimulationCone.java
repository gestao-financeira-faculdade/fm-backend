package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ProjectedSimulationCone(
        UUID simulationId,
        int horizonDays,
        int numScenarios,
        Money var99,
        Money medianP50,
        Percentage ruinProbabilityPct,
        String executionDevice,
        Instant createdAt,
        List<DailyPercentileBand> dailyBands
) {

    public ProjectedSimulationCone {
        Objects.requireNonNull(simulationId, "simulationId must not be null");
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("horizonDays must be positive");
        }
        if (numScenarios <= 0) {
            throw new IllegalArgumentException("numScenarios must be positive");
        }
        Objects.requireNonNull(var99, "var99 must not be null");
        Objects.requireNonNull(medianP50, "medianP50 must not be null");
        Objects.requireNonNull(ruinProbabilityPct, "ruinProbabilityPct must not be null");
        executionDevice = (executionDevice == null || executionDevice.isBlank()) ? "UNKNOWN" : executionDevice;
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        dailyBands = dailyBands == null ? List.of() : List.copyOf(dailyBands);
    }

    public ProjectedSimulationCone(
            UUID simulationId,
            int horizonDays,
            int numScenarios,
            BigDecimal var99,
            BigDecimal medianP50,
            BigDecimal ruinProbabilityPct,
            String executionDevice,
            Instant createdAt,
            List<DailyPercentileBand> dailyBands
    ) {
        this(
                simulationId,
                horizonDays,
                numScenarios,
                Money.brl(var99),
                Money.brl(medianP50),
                Percentage.of(ruinProbabilityPct),
                executionDevice,
                createdAt,
                dailyBands
        );
    }
}
