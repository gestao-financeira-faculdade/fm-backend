package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PartialSplitSettlementPersistenceStrategy implements SettlementPersistenceStrategy {

    private final FinancialEntryRepositoryPort entryRepository;
    private final AuditTrailPort auditTrailPort;

    public PartialSplitSettlementPersistenceStrategy(
            FinancialEntryRepositoryPort entryRepository,
            AuditTrailPort auditTrailPort
    ) {
        this.entryRepository = entryRepository;
        this.auditTrailPort = auditTrailPort;
    }

    @Override
    public SettlementOutcome execute(FinancialEntry originalEntry, SettlementOutcome outcome) {
        FinancialEntry settledParent = entryRepository.save(outcome.settledEntry());
        FinancialEntry residualChild = entryRepository.save(outcome.residualChildEntry().orElseThrow());

        Map<String, Object> splitAuditState = new LinkedHashMap<>();
        splitAuditState.put("settledEntry", settledParent);
        splitAuditState.put("residualChildEntry", residualChild);

        auditTrailPort.recordTransition(
                "FINANCIAL_ENTRY",
                settledParent.id(),
                AuditAction.PARTIAL_SETTLE_SPLIT,
                originalEntry,
                splitAuditState
        );
        return new SettlementOutcome(settledParent, Optional.of(residualChild));
    }

    @Override
    public SettlementType getSettlementType() {
        return SettlementType.PARTIAL;
    }
}
