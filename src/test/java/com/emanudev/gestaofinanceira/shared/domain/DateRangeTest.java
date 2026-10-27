package com.emanudev.gestaofinanceira.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    @DisplayName("Should calculate daysBetween and check date inclusion")
    void shouldValidateContainsAndDaysBetween() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        DateRange range = new DateRange(start, end);

        assertThat(range.daysBetween()).isEqualTo(30L);
        assertThat(range.contains(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 3, 15))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(range.contains(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    @DisplayName("Should reject endDate before startDate or null dates")
    void shouldRejectInvalidBounds() {
        LocalDate start = LocalDate.of(2026, 3, 10);
        LocalDate end = LocalDate.of(2026, 3, 9);

        assertThatThrownBy(() -> new DateRange(start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End date must not be before start date");
        assertThatThrownBy(() -> new DateRange(null, end))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateRange(start, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
