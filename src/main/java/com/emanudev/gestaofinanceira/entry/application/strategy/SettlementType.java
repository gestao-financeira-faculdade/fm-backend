package com.emanudev.gestaofinanceira.entry.application.strategy;

import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;

public enum SettlementType {
    FULL,
    PARTIAL;

    public static SettlementType fromOutcome(SettlementOutcome outcome) {
        return outcome != null && outcome.hasResidualChild() ? PARTIAL : FULL;
    }
}
