package com.emanudev.gestaofinanceira.simulation.application.usecase;

import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.GrpcRiskSimulationAdapter;
import com.emanudev.gestaofinanceira.simulation.application.port.out.CashFlowProjectionPort;
import com.emanudev.gestaofinanceira.simulation.application.port.out.SimulationRunRepositoryPort;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunRiskSimulationUseCaseTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-01T12:00:00Z");
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 1);
    private static final UUID ADMIN_ID = UUID.fromString("0195f000-0000-7000-8000-000000000001");

    private StubCashFlowProjectionPort cashFlowProjectionPort;
    private GrpcRiskSimulationAdapter riskSimulationEngineAdapter;
    private InMemorySimulationRunRepository repository;
    private RunRiskSimulationUseCase useCase;

    @BeforeEach
    void setUp() {
        cashFlowProjectionPort = new StubCashFlowProjectionPort();
        riskSimulationEngineAdapter = new GrpcRiskSimulationAdapter();
        repository = new InMemorySimulationRunRepository();
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        useCase = new RunRiskSimulationUseCase(
                cashFlowProjectionPort,
                riskSimulationEngineAdapter,
                repository,
                () -> new ActorContext(ADMIN_ID, "admin@gestaofinanceira.com", "ADMIN", "127.0.0.1", "JUnit"),
                fixedClock
        );
    }

    @Test
    @DisplayName("Deve orquestrar projeção de caixa, executar simulação determinística com seed 42L, validar bandas percentílicas e persistir resultado")
    void shouldOrchestrateDeterministicSimulationAndPersistResult() {
        StressScenarioParams params = new StressScenarioParams(
                30,
                100_000,
                Percentage.of(8.0),
                Percentage.of(15.0),
                42L
        );

        RiskSimulationResult firstRun = useCase.runSimulation(params);
        RiskSimulationResult secondRun = useCase.runSimulation(params);

        // Determinismo matemático estrito sob randomSeed = 42L
        assertThat(firstRun.var99()).isEqualTo(secondRun.var99());
        assertThat(firstRun.medianP50()).isEqualTo(secondRun.medianP50());
        assertThat(firstRun.ruinProbabilityPct()).isEqualTo(secondRun.ruinProbabilityPct());
        assertThat(firstRun.randomSeed()).isEqualTo(42L);
        assertThat(firstRun.requestedBy()).isEqualTo(ADMIN_ID);
        assertThat(firstRun.initialBalance()).isEqualTo(Money.brl("50000.00"));
        assertThat(firstRun.horizonDays()).isEqualTo(30);
        assertThat(firstRun.numScenarios()).isEqualTo(100_000);
        assertThat(firstRun.dailyBands()).hasSize(30);

        // Invariante de ordenação monotônica dos percentis em todos os dias: P01 <= P05 <= P25 <= P50 <= P75 <= P95
        for (int i = 0; i < 30; i++) {
            DailyPercentileBand band1 = firstRun.dailyBands().get(i);
            DailyPercentileBand band2 = secondRun.dailyBands().get(i);

            assertThat(band1.dayOffset()).isEqualTo(i + 1);
            assertThat(band1.date()).isEqualTo(FIXED_DATE.plusDays(i + 1));
            assertThat(band1.p01()).isEqualTo(band2.p01());
            assertThat(band1.p50()).isEqualTo(band2.p50());
            assertThat(band1.p95()).isEqualTo(band2.p95());

            assertThat(band1.p01().isLessThanOrEqual(band1.p05())).isTrue();
            assertThat(band1.p05().isLessThanOrEqual(band1.p25())).isTrue();
            assertThat(band1.p25().isLessThanOrEqual(band1.p50())).isTrue();
            assertThat(band1.p50().isLessThanOrEqual(band1.p75())).isTrue();
            assertThat(band1.p75().isLessThanOrEqual(band1.p95())).isTrue();
        }

        // Persistência e consulta do último resultado e por ID
        assertThat(useCase.getLatestSimulation()).isPresent().contains(secondRun);
        assertThat(useCase.getSimulationById(firstRun.id())).isEqualTo(firstRun);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao consultar simulação inexistente por ID")
    void shouldThrowResourceNotFoundWhenSimulationDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.getSimulationById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static class StubCashFlowProjectionPort implements CashFlowProjectionPort {

        @Override
        public Money calculateCurrentConsolidatedBalance() {
            return Money.brl("50000.00");
        }

        @Override
        public List<ScheduledDailyCashFlow> projectScheduledCashFlows(LocalDate referenceDate, int horizonDays) {
            List<ScheduledDailyCashFlow> schedule = new ArrayList<>(horizonDays);
            for (int day = 1; day <= horizonDays; day++) {
                schedule.add(new ScheduledDailyCashFlow(
                        day,
                        Money.brl("1200.00"),
                        Money.brl("800.00"),
                        Money.brl("2200.00")
                ));
            }
            return schedule;
        }
    }

    private static class InMemorySimulationRunRepository implements SimulationRunRepositoryPort {
        private final Map<UUID, RiskSimulationResult> store = new ConcurrentHashMap<>();
        private volatile RiskSimulationResult latest;

        @Override
        public RiskSimulationResult save(RiskSimulationResult result) {
            store.put(result.id(), result);
            latest = result;
            return result;
        }

        @Override
        public Optional<RiskSimulationResult> findLatest() {
            return Optional.ofNullable(latest);
        }

        @Override
        public Optional<RiskSimulationResult> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }
    }
}
