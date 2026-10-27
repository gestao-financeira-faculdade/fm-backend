package com.emanudev.gestaofinanceira.simulation.domain.model;

import com.emanudev.gestaofinanceira.shared.domain.Money;

public record ScheduledDailyCashFlow(
        int dayOffset,
        Money payableFixed,
        Money payableVariable,
        Money receivable
) {

    public ScheduledDailyCashFlow {
        if (dayOffset < 1) {
            throw new IllegalArgumentException("dayOffset must be >= 1, got: " + dayOffset);
        }
        if (payableFixed == null) {
            payableFixed = Money.zeroBrl();
        }
        if (payableVariable == null) {
            payableVariable = Money.zeroBrl();
        }
        if (receivable == null) {
            receivable = Money.zeroBrl();
        }
    }

    public static ScheduledDailyCashFlow empty(int dayOffset) {
        return new ScheduledDailyCashFlow(dayOffset, Money.zeroBrl(), Money.zeroBrl(), Money.zeroBrl());
    }
}
