package com.emanudev.gestaofinanceira.entry.application.strategy;

import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;

public interface SettlementPersistenceStrategy {

    SettlementOutcome execute(FinancialEntry originalEntry, SettlementOutcome outcome);

    SettlementType getSettlementType();
}
