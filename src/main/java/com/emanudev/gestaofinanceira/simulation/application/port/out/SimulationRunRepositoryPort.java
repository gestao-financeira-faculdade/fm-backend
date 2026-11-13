package com.emanudev.gestaofinanceira.simulation.application.port.out;

import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;

import java.util.Optional;
import java.util.UUID;

public interface SimulationRunRepositoryPort {

    RiskSimulationResult save(RiskSimulationResult result);

    Optional<RiskSimulationResult> findLatest();

    Optional<RiskSimulationResult> findById(UUID id);
}
