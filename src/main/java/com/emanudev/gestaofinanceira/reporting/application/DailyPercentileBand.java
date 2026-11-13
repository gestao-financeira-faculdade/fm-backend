package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public record DailyPercentileBand(
        int dayOffset,
        LocalDate date,
        BigDecimal p01,
        BigDecimal p05,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        BigDecimal p95
) {

    public DailyPercentileBand {
        if (dayOffset < 0) {
            throw new IllegalArgumentException("dayOffset must be >= 0");
        }
        Objects.requireNonNull(date, "date must not be null");
        p01 = scale(Objects.requireNonNull(p01, "p01 must not be null"));
        p05 = scale(Objects.requireNonNull(p05, "p05 must not be null"));
        p25 = scale(Objects.requireNonNull(p25, "p25 must not be null"));
        p50 = scale(Objects.requireNonNull(p50, "p50 must not be null"));
        p75 = scale(Objects.requireNonNull(p75, "p75 must not be null"));
        p95 = scale(Objects.requireNonNull(p95, "p95 must not be null"));
    }

    public DailyPercentileBand(
            int dayOffset,
            LocalDate date,
            Money p01,
            Money p05,
            Money p25,
            Money p50,
            Money p75,
            Money p95
    ) {
        this(
                dayOffset,
                date,
                p01.amount(),
                p05.amount(),
                p25.amount(),
                p50.amount(),
                p75.amount(),
                p95.amount()
        );
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
