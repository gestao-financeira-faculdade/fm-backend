package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RiskSimulationResult(
        UUID id,
        UUID requestedBy,
        int horizonDays,
        int numScenarios,
        Money initialBalance,
        Percentage expectedDefaultRate,
        Percentage variableCostShockPct,
        long randomSeed,
        Money var99,
        Money medianP50,
        Percentage ruinProbabilityPct,
        List<DailyPercentileBand> dailyBands,
        String executionDevice,
        long executionTimeMs,
        Instant createdAt
) {

    public RiskSimulationResult {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(initialBalance, "initialBalance must not be null");
        Objects.requireNonNull(expectedDefaultRate, "expectedDefaultRate must not be null");
        Objects.requireNonNull(variableCostShockPct, "variableCostShockPct must not be null");
        Objects.requireNonNull(var99, "var99 must not be null");
        Objects.requireNonNull(medianP50, "medianP50 must not be null");
        Objects.requireNonNull(ruinProbabilityPct, "ruinProbabilityPct must not be null");
        dailyBands = dailyBands == null ? List.of() : List.copyOf(dailyBands);
        executionDevice = executionDevice == null || executionDevice.isBlank() ? "UNKNOWN" : executionDevice;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
