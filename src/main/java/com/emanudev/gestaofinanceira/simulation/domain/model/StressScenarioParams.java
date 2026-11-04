package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.util.concurrent.ThreadLocalRandom;

public record StressScenarioParams(
        int horizonDays,
        int numScenarios,
        Percentage expectedDefaultRate,
        Percentage variableCostShockPct,
        Long randomSeed
) {

    public static final int MIN_SCENARIOS = 100_000;
    public static final int MAX_SCENARIOS = 1_000_000;

    public StressScenarioParams {
        if (!SimulationHorizon.isValid(horizonDays)) {
            throw new BusinessRuleViolationException(
                    "INVALID_SIMULATION_HORIZON",
                    "Simulation horizon must be 30, 60, or 90 days, but got: " + horizonDays
            );
        }
        if (numScenarios < MIN_SCENARIOS) {
            throw new BusinessRuleViolationException(
                    "INSUFFICIENT_SIMULATION_SCENARIOS",
                    "Number of scenarios must be at least " + MIN_SCENARIOS + " (RN03.2), but got: " + numScenarios
            );
        }
        if (numScenarios > MAX_SCENARIOS) {
            throw new BusinessRuleViolationException(
                    "EXCESSIVE_SIMULATION_SCENARIOS",
                    "Number of scenarios must not exceed " + MAX_SCENARIOS + ", but got: " + numScenarios
            );
        }
        if (expectedDefaultRate == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_DEFAULT_RATE",
                    "Expected default rate must not be null"
            );
        }
        if (variableCostShockPct == null) {
            throw new BusinessRuleViolationException(
                    "INVALID_COST_SHOCK",
                    "Variable cost shock percentage must not be null"
            );
        }
    }

    public long resolvedSeed() {
        return randomSeed != null ? randomSeed : ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }
}
