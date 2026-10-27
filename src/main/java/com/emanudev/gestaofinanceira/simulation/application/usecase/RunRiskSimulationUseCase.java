package com.emanudev.gestaofinanceira.simulation.application.usecase;

import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.simulation.application.port.in.GetLatestSimulationPort;
import com.emanudev.gestaofinanceira.simulation.application.port.in.RunRiskSimulationPort;
import com.emanudev.gestaofinanceira.simulation.application.port.out.CashFlowProjectionPort;
import com.emanudev.gestaofinanceira.simulation.application.port.out.RiskSimulationEnginePort;
import com.emanudev.gestaofinanceira.simulation.application.port.out.SimulationRunRepositoryPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunRiskSimulationUseCase implements RunRiskSimulationPort, GetLatestSimulationPort {

    private final CashFlowProjectionPort cashFlowProjectionPort;
    private final RiskSimulationEnginePort riskSimulationEnginePort;
    private final SimulationRunRepositoryPort simulationRunRepositoryPort;
    private final ActorContextPort actorContextPort;
    private final Clock clock;

    @Autowired
    public RunRiskSimulationUseCase(
            CashFlowProjectionPort cashFlowProjectionPort,
            RiskSimulationEnginePort riskSimulationEnginePort,
            SimulationRunRepositoryPort simulationRunRepositoryPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this.cashFlowProjectionPort = Objects.requireNonNull(cashFlowProjectionPort, "cashFlowProjectionPort must not be null");
        this.riskSimulationEnginePort = Objects.requireNonNull(riskSimulationEnginePort, "riskSimulationEnginePort must not be null");
        this.simulationRunRepositoryPort = Objects.requireNonNull(simulationRunRepositoryPort, "simulationRunRepositoryPort must not be null");
        this.actorContextPort = actorContextPort;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public RunRiskSimulationUseCase(
            CashFlowProjectionPort cashFlowProjectionPort,
            RiskSimulationEnginePort riskSimulationEnginePort,
            SimulationRunRepositoryPort simulationRunRepositoryPort
    ) {
        this(cashFlowProjectionPort, riskSimulationEnginePort, simulationRunRepositoryPort, ActorContext::system, Clock.systemDefaultZone());
    }

    /**
     * Intentionally NOT annotated with @Transactional so that database connections
     * from HikariCP are released before invoking the HPC simulation engine over gRPC.
     */
    @Override
    public RiskSimulationResult runSimulation(StressScenarioParams params) {
        Objects.requireNonNull(params, "StressScenarioParams must not be null");

        LocalDate today = LocalDate.now(clock);
        Money initialBalance = cashFlowProjectionPort.calculateCurrentConsolidatedBalance();
        List<ScheduledDailyCashFlow> scheduledItems = cashFlowProjectionPort.projectScheduledCashFlows(today, params.horizonDays());

        UUID simulationId = UUID.randomUUID();
        UUID requestedBy = resolveRequestedBy();

        RiskSimulationResult result = riskSimulationEnginePort.simulateRisk(
                simulationId,
                requestedBy,
                initialBalance,
                today,
                params,
                scheduledItems
        );

        return simulationRunRepositoryPort.save(result);
    }

    @Override
    public Optional<RiskSimulationResult> getLatestSimulation() {
        return simulationRunRepositoryPort.findLatest();
    }

    @Override
    public RiskSimulationResult getSimulationById(UUID id) {
        return simulationRunRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RiskSimulationResult", id));
    }

    private UUID resolveRequestedBy() {
        if (actorContextPort == null) {
            return null;
        }
        ActorContext actor = actorContextPort.getCurrentActor();
        return actor != null ? actor.userId() : null;
    }
}
