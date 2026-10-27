package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.Percentage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record ExecutiveKpiResponse(
        Money consolidatedBalance,
        Money totalReceivableToday,
        Money totalPayableToday,
        Percentage monthlyDelinquencyRatePct,
        LocalDate referenceDate,
        Instant generatedAt
) {

    public ExecutiveKpiResponse {
        Objects.requireNonNull(consolidatedBalance, "consolidatedBalance must not be null");
        Objects.requireNonNull(totalReceivableToday, "totalReceivableToday must not be null");
        Objects.requireNonNull(totalPayableToday, "totalPayableToday must not be null");
        Objects.requireNonNull(monthlyDelinquencyRatePct, "monthlyDelinquencyRatePct must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
