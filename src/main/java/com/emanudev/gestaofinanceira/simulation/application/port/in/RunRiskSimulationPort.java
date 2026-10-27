package com.emanudev.gestaofinanceira.simulation.application.port.in;

import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;

public interface RunRiskSimulationPort {

    RiskSimulationResult runSimulation(StressScenarioParams params);

    default RiskSimulationResult execute(StressScenarioParams params) {
        return runSimulation(params);
    }
}
