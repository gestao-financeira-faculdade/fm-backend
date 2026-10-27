package com.emanudev.gestaofinanceira.simulation.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.RunSimulationRequest;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.SimulationResponse;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.mapper.SimulationWebMapper;
import com.emanudev.gestaofinanceira.simulation.application.port.in.GetLatestSimulationPort;
import com.emanudev.gestaofinanceira.simulation.application.port.in.RunRiskSimulationPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationController implements SimulationControllerOpenApi {

    private final RunRiskSimulationPort runRiskSimulationPort;
    private final GetLatestSimulationPort getLatestSimulationPort;
    private final SimulationWebMapper simulationWebMapper;

    @Autowired
    public SimulationController(
            RunRiskSimulationPort runRiskSimulationPort,
            GetLatestSimulationPort getLatestSimulationPort,
            SimulationWebMapper simulationWebMapper
    ) {
        this.runRiskSimulationPort = runRiskSimulationPort;
        this.getLatestSimulationPort = getLatestSimulationPort;
        this.simulationWebMapper = simulationWebMapper;
    }

    public SimulationController(
            RunRiskSimulationPort runRiskSimulationPort,
            GetLatestSimulationPort getLatestSimulationPort
    ) {
        this(runRiskSimulationPort, getLatestSimulationPort, new SimulationWebMapper());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SimulationResponse> runSimulation(RunSimulationRequest request) {
        StressScenarioParams params = simulationWebMapper.toDomainParams(request);
        RiskSimulationResult result = runRiskSimulationPort.runSimulation(params);
        return ResponseEntity.status(HttpStatus.CREATED).body(SimulationResponse.fromDomain(result));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
    public ResponseEntity<SimulationResponse> getLatestSimulation() {
        RiskSimulationResult result = getLatestSimulationPort.getLatestSimulation()
                .orElseThrow(() -> new ResourceNotFoundException("RiskSimulationResult", "latest"));
        return ResponseEntity.ok(SimulationResponse.fromDomain(result));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALISTA', 'AUDITOR')")
    public ResponseEntity<SimulationResponse> getSimulationById(UUID id) {
        RiskSimulationResult result = getLatestSimulationPort.getSimulationById(id);
        return ResponseEntity.ok(SimulationResponse.fromDomain(result));
    }
}
