package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FullSettlementPersistenceStrategy implements SettlementPersistenceStrategy {

    private final FinancialEntryRepositoryPort entryRepository;
    private final AuditTrailPort auditTrailPort;

    public FullSettlementPersistenceStrategy(
            FinancialEntryRepositoryPort entryRepository,
            AuditTrailPort auditTrailPort
    ) {
        this.entryRepository = entryRepository;
        this.auditTrailPort = auditTrailPort;
    }

    @Override
    public SettlementOutcome execute(FinancialEntry originalEntry, SettlementOutcome outcome) {
        FinancialEntry settledParent = entryRepository.save(outcome.settledEntry());
        auditTrailPort.recordTransition(
                "FINANCIAL_ENTRY",
                settledParent.id(),
                AuditAction.SETTLE,
                originalEntry,
                settledParent
        );
        return new SettlementOutcome(settledParent, Optional.empty());
    }

    @Override
    public SettlementType getSettlementType() {
        return SettlementType.FULL;
    }
}
