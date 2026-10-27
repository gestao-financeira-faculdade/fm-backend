package com.emanudev.gestaofinanceira.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PercentageTest {

    @ParameterizedTest
    @CsvSource({
            "0,       0.0000,   0.000000",
            "5.25,    5.2500,   0.052500",
            "100,     100.0000, 1.000000",
            "12.3456, 12.3456,  0.123456"
    })
    @DisplayName("Should normalize scale 4 and convert to fraction with scale 6")
    void shouldCreatePercentageAndConvertToFraction(String input, String expectedScale4, String expectedFraction) {
        Percentage percentage = Percentage.of(new BigDecimal(input));

        assertThat(percentage.value()).isEqualByComparingTo(expectedScale4);
        assertThat(percentage.value().scale()).isEqualTo(4);
        assertThat(percentage.toFraction()).isEqualByComparingTo(expectedFraction);
    }

    @Test
    @DisplayName("Should reject negative or above 100 percentage values")
    void shouldRejectOutOfBoundsValues() {
        assertThatThrownBy(() -> Percentage.of(-0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Percentage.of(100.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Percentage.of((BigDecimal) null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
