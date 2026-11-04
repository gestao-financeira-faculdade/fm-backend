package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveAccountValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveCategoryValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.ActiveCounterpartyValidationImpl;
import com.emanudev.gestaofinanceira.entry.application.strategy.impl.CompatibleCategoryTypeValidationImpl;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
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
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreateFinancialEntryUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FinancialAccountRepositoryPort accountRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final AuditTrailPort auditTrailPort;
    private final ActorContextPort actorContextPort;
    private final Clock clock;
    private final List<CreateEntryValidationStrategy> validationStrategies;

    @Autowired
    public CreateFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FinancialAccountRepositoryPort accountRepository,
            CategoryRepositoryPort categoryRepository,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock,
            List<CreateEntryValidationStrategy> validationStrategies
    ) {
        this.entryRepository = entryRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.auditTrailPort = auditTrailPort;
        this.actorContextPort = actorContextPort;
        this.clock = clock;
        this.validationStrategies = validationStrategies;
    }

    public CreateFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FinancialAccountRepositoryPort accountRepository,
            CategoryRepositoryPort categoryRepository,
            CounterpartyRepositoryPort counterpartyRepository,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this(
                entryRepository,
                accountRepository,
                categoryRepository,
                auditTrailPort,
                actorContextPort,
                clock,
                List.of(
                        new ActiveAccountValidationImpl(),
                        new ActiveCategoryValidationImpl(),
                        new CompatibleCategoryTypeValidationImpl(),
                        new ActiveCounterpartyValidationImpl(counterpartyRepository)
                )
        );
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.CREATE, entityType = "FINANCIAL_ENTRY")
    public FinancialEntry execute(CreateEntryCommand command) {
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
        validationStrategies.forEach(validation -> validation.execute(validationContext));

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
}
