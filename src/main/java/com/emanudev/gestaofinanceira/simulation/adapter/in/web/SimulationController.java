package com.emanudev.gestaofinanceira.simulation.adapter.in.web;

import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.simulation.application.port.in.GetLatestSimulationPort;
import com.emanudev.gestaofinanceira.simulation.application.port.in.RunRiskSimulationPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationController implements SimulationControllerOpenApi {

    private final RunRiskSimulationPort runRiskSimulationPort;
    private final GetLatestSimulationPort getLatestSimulationPort;

    public SimulationController(
            RunRiskSimulationPort runRiskSimulationPort,
            GetLatestSimulationPort getLatestSimulationPort
    ) {
        this.runRiskSimulationPort = runRiskSimulationPort;
        this.getLatestSimulationPort = getLatestSimulationPort;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SimulationResponse> runSimulation(RunSimulationRequest request) {
        StressScenarioParams params = toDomainParams(request);
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

    private StressScenarioParams toDomainParams(RunSimulationRequest request) {
        if (request == null) {
            return new StressScenarioParams(
                30,
                StressScenarioParams.MIN_SCENARIOS,
                Percentage.of(5.0),
                Percentage.of(10.0),
                null
            );
        }
        int horizonDays = request.horizonDays() != null ? request.horizonDays() : 30;
        int numScenarios = request.numScenarios() != null ? request.numScenarios() : StressScenarioParams.MIN_SCENARIOS;
        Percentage defaultRate = request.expectedDefaultRate() != null
                ? Percentage.of(request.expectedDefaultRate())
                : Percentage.of(5.0);
        Percentage costShock = request.variableCostShockPct() != null
                ? Percentage.of(request.variableCostShockPct())
                : Percentage.of(10.0);

        return new StressScenarioParams(
                horizonDays,
                numScenarios,
                defaultRate,
                costShock,
                request.randomSeed()
        );
    }

    public record RunSimulationRequest(
            Integer horizonDays,
            Integer numScenarios,
            BigDecimal expectedDefaultRate,
            BigDecimal variableCostShockPct,
            Long randomSeed
    ) {
    }

    public record DailyBandResponse(
            int dayOffset,
            LocalDate date,
            BigDecimal p01,
            BigDecimal p05,
            BigDecimal p25,
            BigDecimal p50,
            BigDecimal p75,
            BigDecimal p95
    ) {
        public static DailyBandResponse fromDomain(DailyPercentileBand band) {
            return new DailyBandResponse(
                    band.dayOffset(),
                    band.date(),
                    band.p01().amount(),
                    band.p05().amount(),
                    band.p25().amount(),
                    band.p50().amount(),
                    band.p75().amount(),
                    band.p95().amount()
            );
        }
    }

    public record SimulationResponse(
            UUID id,
            UUID requestedBy,
            int horizonDays,
            int numScenarios,
            BigDecimal initialBalance,
            BigDecimal expectedDefaultRate,
            BigDecimal variableCostShockPct,
            long randomSeed,
            BigDecimal var99,
            BigDecimal medianP50,
            BigDecimal ruinProbabilityPct,
            List<DailyBandResponse> dailyBands,
            String executionDevice,
            long executionTimeMs,
            Instant createdAt
    ) {
        public static SimulationResponse fromDomain(RiskSimulationResult result) {
            List<DailyBandResponse> bands = result.dailyBands().stream()
                    .map(DailyBandResponse::fromDomain)
                    .toList();
            return new SimulationResponse(
                    result.id(),
                    result.requestedBy(),
                    result.horizonDays(),
                    result.numScenarios(),
                    result.initialBalance().amount(),
                    result.expectedDefaultRate().value(),
                    result.variableCostShockPct().value(),
                    result.randomSeed(),
                    result.var99().amount(),
                    result.medianP50().amount(),
                    result.ruinProbabilityPct().value(),
                    bands,
                    result.executionDevice(),
                    result.executionTimeMs(),
                    result.createdAt()
            );
        }
    }
}
