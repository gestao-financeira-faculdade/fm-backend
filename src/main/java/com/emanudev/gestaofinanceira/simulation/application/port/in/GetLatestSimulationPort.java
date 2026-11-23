package com.emanudev.gestaofinanceira.simulation.application.port.in;

import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;

import java.util.Optional;
import java.util.UUID;

public interface GetLatestSimulationPort {

    Optional<RiskSimulationResult> getLatestSimulation();

    RiskSimulationResult getSimulationById(UUID id);

    default Optional<RiskSimulationResult> findLatest() {
        return getLatestSimulation();
    }
}
