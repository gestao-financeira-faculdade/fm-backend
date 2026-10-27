package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CreateFinancialAccountUseCase {

    private final FinancialAccountRepositoryPort accountRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public CreateFinancialAccountUseCase(
            FinancialAccountRepositoryPort accountRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public FinancialAccount execute(String name, AccountType accountType, Money initialBalance) {
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
}
