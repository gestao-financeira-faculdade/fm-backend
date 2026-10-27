package com.emanudev.gestaofinanceira.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Percentage(BigDecimal value) {

    private static final BigDecimal MIN_VALUE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VALUE = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Percentage {
        if (value == null) {
            throw new IllegalArgumentException("Percentage value must not be null");
        }
        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("Percentage value must be between 0 and 100");
        }
        value = value.setScale(4, RoundingMode.HALF_EVEN);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(double value) {
        return new Percentage(BigDecimal.valueOf(value));
    }

    public static Percentage of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Percentage value string must not be null or blank");
        }
        return new Percentage(new BigDecimal(value.trim()));
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    public BigDecimal toFraction() {
        return value.divide(HUNDRED, 6, RoundingMode.HALF_EVEN);
    }

    public boolean isZero() {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }
}
