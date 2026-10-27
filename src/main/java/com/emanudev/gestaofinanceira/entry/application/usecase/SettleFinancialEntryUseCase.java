package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.FiscalAttachmentRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementType;
import com.emanudev.gestaofinanceira.entry.application.strategy.factory.SettlementStrategyFactory;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.FullSettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.PartialSplitSettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.ConcurrencyConflictException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class SettleFinancialEntryUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final FiscalAttachmentRepositoryPort attachmentRepository;
    private final SettlementStrategyFactory settlementStrategyFactory;
    private final Clock clock;

    @Autowired
    public SettleFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            CategoryRepositoryPort categoryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository,
            SettlementStrategyFactory settlementStrategyFactory,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.attachmentRepository = attachmentRepository;
        this.settlementStrategyFactory = settlementStrategyFactory;
        this.clock = clock;
    }

    public SettleFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            CategoryRepositoryPort categoryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this(
                entryRepository,
                categoryRepository,
                attachmentRepository,
                new SettlementStrategyFactory(Set.of(
                        new FullSettlementPersistenceStrategy(entryRepository, auditTrailPort),
                        new PartialSplitSettlementPersistenceStrategy(entryRepository, auditTrailPort)
                )),
                clock
        );
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.SETTLE, entityType = "FINANCIAL_ENTRY")
    public SettlementOutcome execute(UUID entryId, SettlementCommand command, Long expectedVersion) {
        FinancialEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));

        if (expectedVersion != null && entry.version() != expectedVersion) {
            throw new ConcurrencyConflictException(
                    "Optimistic locking version mismatch for entry %s: expected %d but current is %d"
                            .formatted(entryId, expectedVersion, entry.version())
            );
        }

        Category category = categoryRepository.findById(entry.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", entry.categoryId()));

        UUID effectiveRootId = entry.rootEntryId() != null ? entry.rootEntryId() : entry.id();
        Money rootOriginalAmount = effectiveRootId.equals(entry.id())
                ? entry.originalAmount()
                : entryRepository.findById(effectiveRootId)
                        .map(FinancialEntry::originalAmount)
                        .orElse(entry.originalAmount());

        int attachmentCount = attachmentRepository.countByEntryOrRootEntry(entry.id(), effectiveRootId);
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);

        SettlementOutcome outcome = entry.settle(
                command,
                category.requiresMandatoryFiscalAttachment(),
                category.name(),
                rootOriginalAmount,
                attachmentCount,
                today,
                now
        );

        SettlementType settlementType = SettlementType.fromOutcome(outcome);
        return settlementStrategyFactory.getStrategy(settlementType).execute(entry, outcome);
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.SETTLE, entityType = "FINANCIAL_ENTRY")
    public SettlementOutcome execute(UUID entryId, SettlementCommand command) {
        return execute(entryId, command, null);
    }
}
