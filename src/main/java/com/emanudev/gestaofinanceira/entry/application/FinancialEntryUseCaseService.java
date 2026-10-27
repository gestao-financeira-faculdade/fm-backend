package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.SettlementType;
import com.emanudev.gestaofinanceira.entry.application.strategy.factory.SettlementStrategyFactory;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveAccountValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveCategoryValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveCounterpartyValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.CompatibleCategoryTypeValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.FullSettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.PartialSplitSettlementPersistenceStrategy;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ConcurrencyConflictException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FinancialEntryUseCaseService {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FinancialEntryQueryPort entryQueryPort;
    private final FiscalAttachmentRepositoryPort attachmentRepository;
    private final AttachmentStoragePort attachmentStoragePort;
    private final FinancialAccountRepositoryPort accountRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final AuditTrailPort auditTrailPort;
    private final ActorContextPort actorContextPort;
    private final Clock clock;
    private final FiscalAttachmentValidator attachmentValidator;
    private final List<CreateEntryValidationStrategy> createEntryValidationStrategies;
    private final SettlementStrategyFactory settlementStrategyFactory;

    @Autowired
    public FinancialEntryUseCaseService(
            FinancialEntryRepositoryPort entryRepository,
            FinancialEntryQueryPort entryQueryPort,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AttachmentStoragePort attachmentStoragePort,
            FinancialAccountRepositoryPort accountRepository,
            CategoryRepositoryPort categoryRepository,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock,
            FiscalAttachmentValidator attachmentValidator,
            List<CreateEntryValidationStrategy> createEntryValidationStrategies,
            SettlementStrategyFactory settlementStrategyFactory
    ) {
        this.entryRepository = entryRepository;
        this.entryQueryPort = entryQueryPort;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStoragePort = attachmentStoragePort;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.auditTrailPort = auditTrailPort;
        this.actorContextPort = actorContextPort;
        this.clock = clock;
        this.attachmentValidator = attachmentValidator;
        this.createEntryValidationStrategies = createEntryValidationStrategies;
        this.settlementStrategyFactory = settlementStrategyFactory;
    }

    public FinancialEntryUseCaseService(
            FinancialEntryRepositoryPort entryRepository,
            FinancialEntryQueryPort entryQueryPort,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AttachmentStoragePort attachmentStoragePort,
            FinancialAccountRepositoryPort accountRepository,
            CategoryRepositoryPort categoryRepository,
            CounterpartyRepositoryPort counterpartyRepository,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this(
                entryRepository,
                entryQueryPort,
                attachmentRepository,
                attachmentStoragePort,
                accountRepository,
                categoryRepository,
                auditTrailPort,
                actorContextPort,
                clock,
                new FiscalAttachmentValidator(),
                List.of(
                        new ActiveAccountValidationImpl(),
                        new ActiveCategoryValidationImpl(),
                        new CompatibleCategoryTypeValidationImpl(),
                        new ActiveCounterpartyValidationImpl(counterpartyRepository)
                ),
                new SettlementStrategyFactory(Set.of(
                        new FullSettlementPersistenceStrategy(entryRepository, auditTrailPort),
                        new PartialSplitSettlementPersistenceStrategy(entryRepository, auditTrailPort)
                ))
        );
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.CREATE, entityType = "FINANCIAL_ENTRY")
    public FinancialEntry createEntry(CreateEntryCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_COMMAND", "Create entry command must not be null");
        }

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<FinancialEntry> existing = entryRepository.findByIdempotencyKey(command.idempotencyKey().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        UUID resolvedAccountId = resolveAccountId(command.accountId());
        FinancialAccount account = accountRepository.findById(resolvedAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialAccount", resolvedAccountId));

        Category category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", command.categoryId()));

        CreateEntryValidationContext validationContext = new CreateEntryValidationContext(command, account, category);
        createEntryValidationStrategies.forEach(validation -> validation.execute(validationContext));

        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        LocalDate effectiveCompetenceDate = command.competenceDate() != null ? command.competenceDate() : command.dueDate();
        UUID actorId = currentActorUserId();

        FinancialEntry entry = FinancialEntry.create(
                command.idempotencyKey(),
                account.id(),
                category.id(),
                command.counterpartyId(),
                command.entryType(),
                command.description(),
                effectiveCompetenceDate,
                command.dueDate(),
                command.originalAmount(),
                actorId,
                today,
                now
        );

        FinancialEntry saved = entryRepository.save(entry);
        auditTrailPort.recordTransition("FINANCIAL_ENTRY", saved.id(), AuditAction.CREATE, null, saved);
        return saved;
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.SETTLE, entityType = "FINANCIAL_ENTRY")
    public SettlementOutcome settleEntry(UUID entryId, SettlementCommand command, Long expectedVersion) {
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
    public SettlementOutcome settleEntry(UUID entryId, SettlementCommand command) {
        return settleEntry(entryId, command, null);
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.CANCEL_SOFT_DELETE, entityType = "FINANCIAL_ENTRY")
    public FinancialEntry cancelEntry(UUID entryId, String reason) {
        FinancialEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));

        boolean hasActiveChildren = entryRepository.hasActiveChildren(entryId);
        UUID actorId = currentActorUserId();
        Instant now = Instant.now(clock);

        FinancialEntry cancelled = entry.cancel(reason, actorId, now, hasActiveChildren);
        FinancialEntry saved = entryRepository.save(cancelled);

        auditTrailPort.recordTransition(
                "FINANCIAL_ENTRY",
                saved.id(),
                AuditAction.CANCEL_SOFT_DELETE,
                entry,
                saved
        );
        return saved;
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.UPLOAD_ATTACHMENT, entityType = "FINANCIAL_ENTRY")
    public FiscalAttachment uploadAttachment(UUID entryId, String originalFileName, byte[] fileBytes) {
        FinancialEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));

        FiscalAttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(originalFileName, fileBytes);

        UUID attachmentId = UUID.randomUUID();
        String storageKey = attachmentStoragePort.store(
                entryId,
                attachmentId,
                validated.sanitizedFileName(),
                fileBytes
        );

        FiscalAttachment attachment = new FiscalAttachment(
                attachmentId,
                entryId,
                validated.sanitizedFileName(),
                storageKey,
                validated.detectedContentType(),
                validated.fileSizeBytes(),
                validated.sha256Checksum(),
                currentActorUserId(),
                Instant.now(clock)
        );

        FiscalAttachment saved = attachmentRepository.save(attachment);
        auditTrailPort.recordTransition(
                "FINANCIAL_ENTRY",
                entry.id(),
                AuditAction.UPLOAD_ATTACHMENT,
                entry,
                saved
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FiscalAttachment> listAttachments(UUID entryId) {
        if (entryRepository.findById(entryId).isEmpty()) {
            throw new ResourceNotFoundException("FinancialEntry", entryId);
        }
        return attachmentRepository.findByEntryId(entryId);
    }

    @Transactional(readOnly = true)
    public DownloadedAttachment downloadAttachment(UUID entryId, UUID attachmentId) {
        if (entryRepository.findById(entryId).isEmpty()) {
            throw new ResourceNotFoundException("FinancialEntry", entryId);
        }
        FiscalAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("FiscalAttachment", attachmentId));
        if (!attachment.entryId().equals(entryId)) {
            throw new ResourceNotFoundException("FiscalAttachment", attachmentId);
        }
        byte[] content = attachmentStoragePort.load(attachment.storageKey());
        return new DownloadedAttachment(attachment, content);
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.AUTO_OVERDUE_TRANSITION, entityType = "FINANCIAL_ENTRY")
    public int markOverdueEntries() {
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

    @Transactional(readOnly = true)
    public PagedResult<FinancialEntrySummaryView> findEntries(FinancialEntryFilter filter, PageQuery pageQuery) {
        FinancialEntryFilter effectiveFilter = filter != null ? filter : FinancialEntryFilter.empty();
        PageQuery effectivePageQuery = pageQuery != null ? pageQuery : PageQuery.of(0, 20);
        return entryQueryPort.findEntries(effectiveFilter, effectivePageQuery);
    }

    @Transactional(readOnly = true)
    public FinancialEntrySummaryView getEntrySummaryById(UUID entryId) {
        return entryQueryPort.findEntrySummaryById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));
    }

    @Transactional(readOnly = true)
    public FinancialEntry getEntryById(UUID entryId) {
        return entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));
    }

    @Transactional(readOnly = true)
    public List<FinancialEntryLineageNode> getEntryLineage(UUID entryId) {
        if (entryRepository.findById(entryId).isEmpty()) {
            throw new ResourceNotFoundException("FinancialEntry", entryId);
        }
        return entryQueryPort.getEntryLineage(entryId);
    }

    private UUID resolveAccountId(UUID requestedAccountId) {
        if (requestedAccountId != null) {
            return requestedAccountId;
        }
        return accountRepository.findAllActive().stream()
                .findFirst()
                .map(FinancialAccount::id)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "INVALID_ACCOUNT_ID",
                        "Account id must not be null and no default active account was found"
                ));
    }

    private UUID currentActorUserId() {
        if (actorContextPort == null) {
            return null;
        }
        ActorContext actor = actorContextPort.getCurrentActor();
        return actor != null ? actor.userId() : null;
    }

    public record CreateEntryCommand(
            String idempotencyKey,
            UUID accountId,
            UUID categoryId,
            UUID counterpartyId,
            EntryType entryType,
            String description,
            LocalDate competenceDate,
            LocalDate dueDate,
            Money originalAmount
    ) {
    }

    public record DownloadedAttachment(
            FiscalAttachment attachment,
            byte[] content
    ) {
    }
}
