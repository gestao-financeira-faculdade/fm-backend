package com.emanudev.gestaofinanceira.reporting.application;

import java.util.List;
import java.util.Optional;

public record UncertaintyConeResponse(
        List<HistoricalBalancePoint> historicalSeries,
        Optional<ProjectedSimulationCone> projectionCone
) {

    public UncertaintyConeResponse {
        historicalSeries = historicalSeries == null ? List.of() : List.copyOf(historicalSeries);
        projectionCone = projectionCone == null ? Optional.empty() : projectionCone;
    }
}
