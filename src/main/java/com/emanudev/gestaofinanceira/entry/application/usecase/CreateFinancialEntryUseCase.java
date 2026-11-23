package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.application.CreateEntryResult;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreateFinancialEntryUseCase {

    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";

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
    public CreateEntryResult execute(CreateEntryCommand command) {
        if (command == null) {
            throw new BusinessRuleViolationException("INVALID_ENTRY_COMMAND", "Create entry command must not be null");
        }

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<FinancialEntry> existing = entryRepository.findByIdempotencyKey(command.idempotencyKey().trim());
            if (existing.isPresent()) {
                if (!isSameRequest(existing.get(), command)) {
                    throw new BusinessRuleViolationException(
                            IDEMPOTENCY_KEY_REUSED,
                            "Idempotency-Key was already used for a different request"
                    );
                }
                auditTrailPort.skipAuditForCurrentAction();
                return new CreateEntryResult(existing.get(), true);
            }
        }

        UUID resolvedAccountId = resolveAccountId(command.accountId());
        FinancialAccount account = accountRepository.findById(resolvedAccountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Financial account not found: " + resolvedAccountId));

        Category category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new BusinessRuleViolationException("CATEGORY_NOT_FOUND", "Category not found: " + command.categoryId()));

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
        return new CreateEntryResult(saved, false);
    }

    private static boolean isSameRequest(FinancialEntry original, CreateEntryCommand command) {
        LocalDate requestedCompetence = command.competenceDate() != null ? command.competenceDate() : command.dueDate();
        return Objects.equals(original.entryType(), command.entryType())
                && Objects.equals(original.originalAmount(), command.originalAmount())
                && Objects.equals(original.dueDate(), command.dueDate())
                && Objects.equals(original.competenceDate(), requestedCompetence)
                && Objects.equals(original.categoryId(), command.categoryId())
                && Objects.equals(original.counterpartyId(), command.counterpartyId())
                && (command.accountId() == null || Objects.equals(original.accountId(), command.accountId()))
                && Objects.equals(trimmed(original.description()), trimmed(command.description()));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
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
