package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.Money;

import java.time.LocalDate;
import java.util.Objects;

public record DailyPercentileBand(
        int dayOffset,
        LocalDate date,
        Money p01,
        Money p05,
        Money p25,
        Money p50,
        Money p75,
        Money p95
) {

    public DailyPercentileBand {
        if (dayOffset < 1) {
            throw new IllegalArgumentException("dayOffset must be >= 1");
        }
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(p01, "p01 must not be null");
        Objects.requireNonNull(p05, "p05 must not be null");
        Objects.requireNonNull(p25, "p25 must not be null");
        Objects.requireNonNull(p50, "p50 must not be null");
        Objects.requireNonNull(p75, "p75 must not be null");
        Objects.requireNonNull(p95, "p95 must not be null");
    }
}
