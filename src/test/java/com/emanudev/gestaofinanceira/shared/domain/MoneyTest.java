package com.emanudev.gestaofinanceira.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "input={0} should round HALF_EVEN to {1} ({2} cents)")
    @CsvSource({
            "100.005, 100.00, 10000",
            "100.015, 100.02, 10002",
            "100.025, 100.02, 10002",
            "100.035, 100.04, 10004",
            "0.00,    0.00,   0",
            "0.01,    0.01,   1",
            "-15.555, -15.56, -1556"
    })
    @DisplayName("Should apply ABNT NBR 5891 RoundingMode.HALF_EVEN and convert to cents")
    void shouldNormalizeScaleWithHalfEvenAndConvertToCents(String rawInput, String expectedScaled, long expectedCents) {
        Money money = Money.brl(rawInput);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal(expectedScaled));
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.currency()).isEqualTo("BRL");
        assertThat(money.toCents()).isEqualTo(expectedCents);
        assertThat(Money.ofCentsBrl(expectedCents)).isEqualTo(money);
    }

    @ParameterizedTest
    @CsvSource({
            "100.50, 49.50, 150.00, 51.00",
            "0.01,   0.01,  0.02,   0.00",
            "10.00,  25.00, 35.00,  -15.00"
    })
    @DisplayName("Should add and subtract money values with same currency")
    void shouldAddAndSubtractSameCurrency(String first, String second, String expectedSum, String expectedDiff) {
        Money a = Money.brl(first);
        Money b = Money.brl(second);

        assertThat(a.add(b).amount()).isEqualByComparingTo(expectedSum);
        assertThat(a.subtract(b).amount()).isEqualByComparingTo(expectedDiff);
    }

    @Test
    @DisplayName("Should reject arithmetic and comparisons across distinct currencies")
    void shouldRejectDifferentCurrencies() {
        Money brl = Money.of(new BigDecimal("100.00"), "brl");
        Money usd = Money.of(new BigDecimal("100.00"), "USD");

        assertThat(brl.currency()).isEqualTo("BRL");
        assertThatThrownBy(() -> brl.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> brl.subtract(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> brl.isGreaterThan(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should multiply money by factor and Percentage")
    void shouldMultiplyByFactorAndPercentage() {
        Money base = Money.brl("1000.00");
        Percentage rate = Percentage.of(new BigDecimal("12.50"));

        Money result = base.multiply(rate);

        assertThat(result.amount()).isEqualByComparingTo("125.00");
        assertThat(result.isPositive()).isTrue();
        assertThat(result.isZero()).isFalse();
        assertThat(result.isNegative()).isFalse();
    }

    @Test
    @DisplayName("Should evaluate comparison methods accurately")
    void shouldCompareMoneyValues() {
        Money low = Money.brl("50.00");
        Money high = Money.brl("100.00");
        Money sameAsHigh = Money.brl(100.00);

        assertThat(high.isGreaterThan(low)).isTrue();
        assertThat(high.isGreaterThanOrEqual(sameAsHigh)).isTrue();
        assertThat(low.isLessThan(high)).isTrue();
        assertThat(Money.zeroBrl().isZero()).isTrue();
    }

    @Test
    @DisplayName("Should reject null amount or invalid ISO currency code")
    void shouldRejectInvalidConstructorArguments() {
        assertThatThrownBy(() -> Money.of(null, "BRL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, "BR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
