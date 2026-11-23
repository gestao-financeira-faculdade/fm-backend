package com.emanudev.gestaofinanceira.simulation.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.simulation.domain.model.DailyPercentileBand;
import java.math.BigDecimal;
import java.time.LocalDate;

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
