package com.emanudev.gestaofinanceira.entry.domain;

import java.util.Optional;

public record SettlementOutcome(
        FinancialEntry settledEntry,
        Optional<FinancialEntry> residualChildEntry
) {

    public SettlementOutcome {
        if (settledEntry == null) {
            throw new IllegalArgumentException("Settled entry must not be null");
        }
        residualChildEntry = residualChildEntry == null ? Optional.empty() : residualChildEntry;
    }

    public boolean hasResidualChild() {
        return residualChildEntry.isPresent();
    }
}
