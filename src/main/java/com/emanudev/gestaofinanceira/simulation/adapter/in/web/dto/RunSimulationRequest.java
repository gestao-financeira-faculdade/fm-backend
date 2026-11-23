package com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto;

import java.math.BigDecimal;

public record RunSimulationRequest(
        Integer horizonDays,
        Integer numScenarios,
        BigDecimal expectedDefaultRate,
        BigDecimal variableCostShockPct,
        Long randomSeed
) {
}
