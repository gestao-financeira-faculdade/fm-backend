package com.emanudev.gestaofinanceira.shared.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record DateRange(LocalDate startDate, LocalDate endDate) {

    public DateRange {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date must not be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before start date");
        }
    }

    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(startDate, endDate);
    }

    public boolean contains(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date to check must not be null");
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public long daysBetween() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
}
