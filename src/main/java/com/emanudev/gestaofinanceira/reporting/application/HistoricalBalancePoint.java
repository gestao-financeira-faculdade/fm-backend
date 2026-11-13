package com.emanudev.gestaofinanceira.reporting.application;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record HistoricalBalancePoint(
        LocalDate date,
        Money balance
) {

    public HistoricalBalancePoint {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
    }

    public HistoricalBalancePoint(LocalDate date, BigDecimal amount) {
        this(date, Money.brl(amount));
    }

    public BigDecimal realizedBalance() {
        return balance.amount();
    }
}
