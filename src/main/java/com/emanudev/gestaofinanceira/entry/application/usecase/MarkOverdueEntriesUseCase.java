package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class MarkOverdueEntriesUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public MarkOverdueEntriesUseCase(
            FinancialEntryRepositoryPort entryRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.AUTO_OVERDUE_TRANSITION, entityType = "FINANCIAL_ENTRY")
    public int execute() {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        List<FinancialEntry> candidates = entryRepository.findOverdueCandidates(today);

        int transitionedCount = 0;
        for (FinancialEntry candidate : candidates) {
            FinancialEntry updated = candidate.markOverdue(today, now);
            if (updated.status() == EntryStatus.OVERDUE && candidate.status() != EntryStatus.OVERDUE) {
                FinancialEntry saved = entryRepository.save(updated);
                auditTrailPort.recordTransition(
                        "FINANCIAL_ENTRY",
                        saved.id(),
                        AuditAction.AUTO_OVERDUE_TRANSITION,
                        candidate,
                        saved
                );
                transitionedCount++;
            }
        }
        return transitionedCount;
    }
}
