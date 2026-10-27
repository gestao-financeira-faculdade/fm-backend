package com.emanudev.gestaofinanceira.simulation.application.port.out;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RiskSimulationEnginePort {

    RiskSimulationResult simulateRisk(
            UUID simulationId,
            UUID requestedBy,
            Money initialBalance,
            LocalDate referenceDate,
            StressScenarioParams params,
            List<ScheduledDailyCashFlow> scheduledItems
    );

    default RiskSimulationResult simulateRisk(
            Money initialBalance,
            LocalDate referenceDate,
            StressScenarioParams params,
            List<ScheduledDailyCashFlow> scheduledItems
    ) {
        return simulateRisk(UUID.randomUUID(), null, initialBalance, referenceDate, params, scheduledItems);
    }

    default RiskSimulationResult simulateRisk(
            Money initialBalance,
            StressScenarioParams params,
            List<ScheduledDailyCashFlow> scheduledItems
    ) {
        return simulateRisk(UUID.randomUUID(), null, initialBalance, LocalDate.now(), params, scheduledItems);
    }
}
