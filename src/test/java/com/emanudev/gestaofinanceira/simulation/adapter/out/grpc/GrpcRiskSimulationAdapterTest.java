package com.emanudev.gestaofinanceira.simulation.adapter.out.grpc;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.RiskSimulationRequest;
import com.emanudev.gestaofinanceira.simulation.adapter.out.grpc.proto.RiskSimulationResponse;
import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import com.emanudev.gestaofinanceira.simulation.domain.model.ScheduledDailyCashFlow;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcRiskSimulationAdapterTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 26);
    private static final long FIXED_SEED = 20261026L;

    private GrpcRiskSimulationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GrpcRiskSimulationAdapter();
    }

    @Test
    void shouldBuildProtobufRequestConvertingMoneyToCentsAndPercentagesToFractions() {
        StressScenarioParams params = params(30, "5.00", "10.00");
        List<ScheduledDailyCashFlow> items = List.of(
                new ScheduledDailyCashFlow(1, Money.brl("100.00"), Money.brl("50.00"), Money.brl("300.00"))
        );

        RiskSimulationRequest request = adapter.buildProtobufRequest(Money.brl("1000.00"), params, FIXED_SEED, items);

        assertThat(request.getInitialBalanceCents()).isEqualTo(100_000L);
        assertThat(request.getExpectedDefaultRate()).isEqualTo(0.05);
        assertThat(request.getVariableCostShockPct()).isEqualTo(0.10);
        assertThat(request.getRandomSeed()).isEqualTo(FIXED_SEED);
        assertThat(request.getScheduledItemsList()).hasSize(1);
        assertThat(request.getScheduledItems(0).getReceivableCents()).isEqualTo(30_000L);
    }

    @Test
    void shouldProduceDeterministicResultsForSameSeedOnCpuFallback() {
        RiskSimulationRequest request = adapter.buildProtobufRequest(
                Money.brl("10000.00"), params(30, "5.00", "10.00"), FIXED_SEED, cashFlows()
        );

        RiskSimulationResponse first = adapter.simulateDeterministicCpu(request);
        RiskSimulationResponse second = adapter.simulateDeterministicCpu(request);

        assertThat(first.getVar99Cents()).isEqualTo(second.getVar99Cents());
        assertThat(first.getMedianP50Cents()).isEqualTo(second.getMedianP50Cents());
        assertThat(first.getRuinProbabilityPct()).isEqualTo(second.getRuinProbabilityPct());
        assertThat(first.getDailyBandsList()).isEqualTo(second.getDailyBandsList());
    }

    @Test
    void shouldReturnMonotonicPercentileBandsForEveryDayOfHorizon() {
        RiskSimulationRequest request = adapter.buildProtobufRequest(
                Money.brl("10000.00"), params(30, "20.00", "30.00"), FIXED_SEED, cashFlows()
        );

        RiskSimulationResponse response = adapter.simulateDeterministicCpu(request);

        assertThat(response.getDailyBandsList()).hasSize(30);
        response.getDailyBandsList().forEach(band -> {
            assertThat(band.getP01Cents()).isLessThanOrEqualTo(band.getP05Cents());
            assertThat(band.getP05Cents()).isLessThanOrEqualTo(band.getP25Cents());
            assertThat(band.getP25Cents()).isLessThanOrEqualTo(band.getP50Cents());
            assertThat(band.getP50Cents()).isLessThanOrEqualTo(band.getP75Cents());
            assertThat(band.getP75Cents()).isLessThanOrEqualTo(band.getP95Cents());
        });
        assertThat(response.getExecutionDevice()).isEqualTo("CPU-Fallback-SIMD-SplitMix64");
    }

    @Test
    void shouldReportFullRuinProbabilityWhenFixedPayablesExceedBalanceWithoutStochasticShocks() {
        List<ScheduledDailyCashFlow> items = List.of(
                new ScheduledDailyCashFlow(1, Money.brl("2000.00"), Money.zeroBrl(), Money.zeroBrl())
        );
        RiskSimulationRequest request = adapter.buildProtobufRequest(
                Money.brl("1000.00"), params(30, "0.00", "0.00"), FIXED_SEED, items
        );

        RiskSimulationResponse response = adapter.simulateDeterministicCpu(request);

        assertThat(response.getRuinProbabilityPct()).isEqualTo(100.0);
        assertThat(response.getMedianP50Cents()).isEqualTo(-100_000L);
    }

    @Test
    void shouldMapSimulationToDomainResultWhenGrpcIsDisabled() {
        UUID simulationId = UUID.randomUUID();
        StressScenarioParams params = params(60, "5.00", "10.00");

        RiskSimulationResult result = adapter.simulateRisk(
                simulationId, null, Money.brl("50000.00"), REFERENCE_DATE, params, cashFlows()
        );

        assertThat(result.id()).isEqualTo(simulationId);
        assertThat(result.horizonDays()).isEqualTo(60);
        assertThat(result.randomSeed()).isEqualTo(FIXED_SEED);
        assertThat(result.dailyBands()).hasSize(60);
        DailyPercentileBand firstBand = result.dailyBands().getFirst();
        assertThat(firstBand.date()).isEqualTo(REFERENCE_DATE.plusDays(1));
    }

    private static StressScenarioParams params(int horizon, String defaultRate, String costShock) {
        return new StressScenarioParams(
                horizon,
                StressScenarioParams.MIN_SCENARIOS,
                Percentage.of(defaultRate),
                Percentage.of(costShock),
                FIXED_SEED
        );
    }

    private static List<ScheduledDailyCashFlow> cashFlows() {
        return List.of(
                new ScheduledDailyCashFlow(1, Money.brl("1500.00"), Money.brl("800.00"), Money.brl("2500.00")),
                new ScheduledDailyCashFlow(5, Money.brl("3000.00"), Money.brl("1200.00"), Money.brl("4000.00")),
                new ScheduledDailyCashFlow(15, Money.brl("2000.00"), Money.brl("600.00"), Money.brl("1000.00"))
        );
    }
}
