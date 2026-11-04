package com.emanudev.gestaofinanceira.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "BRL";

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount must not be null");
        }
        if (currency == null || currency.trim().length() != 3) {
            throw new IllegalArgumentException("Currency must be a valid 3-character ISO-4217 code");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money brl(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money brl(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Money amount string must not be null or blank");
        }
        return new Money(new BigDecimal(amount.trim()), DEFAULT_CURRENCY);
    }

    public static Money brl(double amount) {
        return new Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    public static Money zeroBrl() {
        return new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money ofCents(long cents, String currency) {
        return new Money(BigDecimal.valueOf(cents, 2), currency);
    }

    public static Money ofCentsBrl(long cents) {
        return ofCents(cents, DEFAULT_CURRENCY);
    }

    public long toCents() {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("Factor must not be null");
        }
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money multiply(Percentage percentage) {
        if (percentage == null) {
            throw new IllegalArgumentException("Percentage must not be null");
        }
        return multiply(percentage.toFraction());
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isLessThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }

    private void assertSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Other money operand must not be null");
        }
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: cannot operate on %s and %s".formatted(this.currency, other.currency)
            );
        }
    }
}
