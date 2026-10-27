package com.emanudev.gestaofinanceira.reference.application;

import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReferenceDataService {

    private final FinancialAccountRepositoryPort accountRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final CounterpartyRepositoryPort counterpartyRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public ReferenceDataService(
            FinancialAccountRepositoryPort accountRepository,
            CategoryRepositoryPort categoryRepository,
            CounterpartyRepositoryPort counterpartyRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public FinancialAccount createAccount(String name, AccountType accountType, Money initialBalance) {
        accountRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                    "DUPLICATE_ACCOUNT_NAME",
                    "Financial account already exists with name: " + existing.name()
            );
        });
        Instant now = Instant.now(clock);
        FinancialAccount created = accountRepository.save(
                FinancialAccount.create(name, accountType, initialBalance, now)
        );
        auditTrailPort.recordTransition("FINANCIAL_ACCOUNT", created.id(), AuditAction.CREATE, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<FinancialAccount> listAccounts() {
        return accountRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public FinancialAccount getAccountById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialAccount", id));
    }

    @Transactional
    public Category createCategory(
            String name,
            CategoryType categoryType,
            boolean requiresAttachmentAlways,
            boolean fixedCost
    ) {
        categoryRepository.findByNameAndType(name.trim(), categoryType).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                    "DUPLICATE_CATEGORY",
                    "Category already exists with name '%s' and type '%s'".formatted(existing.name(), existing.categoryType())
            );
        });
        Instant now = Instant.now(clock);
        Category created = categoryRepository.save(
                Category.create(name, categoryType, requiresAttachmentAlways, fixedCost, now)
        );
        auditTrailPort.recordTransition("CATEGORY", created.id(), AuditAction.CREATE, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public Counterparty createCounterparty(
            String legalName,
            String tradeName,
            String taxId,
            CounterpartyType counterpartyType
    ) {
        if (taxId != null && !taxId.isBlank()) {
            counterpartyRepository.findByTaxId(taxId.trim()).ifPresent(existing -> {
                throw new BusinessRuleViolationException(
                        "DUPLICATE_COUNTERPARTY_TAX_ID",
                        "Counterparty already exists with taxId: " + existing.taxId()
                );
            });
        }
        Instant now = Instant.now(clock);
        Counterparty created = counterpartyRepository.save(
                Counterparty.create(legalName, tradeName, taxId, counterpartyType, now)
        );
        auditTrailPort.recordTransition("COUNTERPARTY", created.id(), AuditAction.CREATE, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Counterparty> listCounterparties() {
        return counterpartyRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Counterparty getCounterpartyById(UUID id) {
        return counterpartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty", id));
    }
}
