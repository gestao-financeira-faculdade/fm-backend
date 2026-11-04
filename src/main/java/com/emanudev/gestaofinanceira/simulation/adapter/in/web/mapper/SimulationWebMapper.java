package com.emanudev.gestaofinanceira.simulation.adapter.in.web.mapper;

import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto.RunSimulationRequest;
import com.emanudev.gestaofinanceira.simulation.domain.model.StressScenarioParams;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SimulationWebMapper {

    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final double DEFAULT_EXPECTED_DEFAULT_RATE = 5.0;
    private static final double DEFAULT_VARIABLE_COST_SHOCK_PCT = 10.0;

    public StressScenarioParams toDomainParams(RunSimulationRequest request) {
        if (request == null) {
            return new StressScenarioParams(
                    DEFAULT_HORIZON_DAYS,
                    StressScenarioParams.MIN_SCENARIOS,
                    Percentage.of(DEFAULT_EXPECTED_DEFAULT_RATE),
                    Percentage.of(DEFAULT_VARIABLE_COST_SHOCK_PCT),
                    null
            );
        }
        int horizonDays = Optional.ofNullable(request.horizonDays()).orElse(DEFAULT_HORIZON_DAYS);
        int numScenarios = Optional.ofNullable(request.numScenarios()).orElse(StressScenarioParams.MIN_SCENARIOS);
        Percentage defaultRate = Optional.ofNullable(request.expectedDefaultRate())
                .map(Percentage::of)
                .orElseGet(() -> Percentage.of(DEFAULT_EXPECTED_DEFAULT_RATE));
        Percentage costShock = Optional.ofNullable(request.variableCostShockPct())
                .map(Percentage::of)
                .orElseGet(() -> Percentage.of(DEFAULT_VARIABLE_COST_SHOCK_PCT));

        return new StressScenarioParams(
                horizonDays,
                numScenarios,
                defaultRate,
                costShock,
                request.randomSeed()
        );
    }
}
