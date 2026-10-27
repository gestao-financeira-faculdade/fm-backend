package com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.simulation.domain.model.RiskSimulationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
