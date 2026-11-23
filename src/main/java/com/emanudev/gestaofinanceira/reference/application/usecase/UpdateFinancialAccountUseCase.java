package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.PatchFinancialAccountCommand;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateFinancialAccountUseCase {

    private final FinancialAccountRepositoryPort accountRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public UpdateFinancialAccountUseCase(FinancialAccountRepositoryPort accountRepository, AuditTrailPort auditTrailPort, Clock clock) {
        this.accountRepository = accountRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public FinancialAccount patch(UUID id, PatchFinancialAccountCommand command) {
        FinancialAccount current = load(id);
        FinancialAccount updated = accountRepository.save(apply(current, command, Instant.now(clock)));
        auditTrailPort.recordTransition("FINANCIAL_ACCOUNT", id, AuditAction.UPDATE, current, updated);
        return updated;
    }

    @Transactional
    public void deactivate(UUID id) {
        FinancialAccount current = load(id);
        if (!current.active()) {
            return;
        }
        Instant now = Instant.now(clock);
        FinancialAccount inactive = new FinancialAccount(current.id(), current.name(), current.accountType(), current.initialBalance(),
                false, current.createdAt(), now);
        FinancialAccount deactivated = accountRepository.save(inactive);
        auditTrailPort.recordTransition("FINANCIAL_ACCOUNT", id, AuditAction.UPDATE, current, deactivated);
    }

    private FinancialAccount load(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("FinancialAccount", id));
    }

    private FinancialAccount apply(FinancialAccount current, PatchFinancialAccountCommand command, Instant now) {
        String name = command.name() != null ? command.name().trim() : current.name();
        if (command.name() != null) {
            accountRepository.findByNameIgnoreCase(name)
                    .filter(other -> !other.id().equals(current.id()))
                    .ifPresent(other -> {
                        throw new BusinessRuleViolationException(
                                "DUPLICATE_ACCOUNT_NAME",
                                "Financial account already exists with name: " + other.name()
                        );
                    });
        }
        return new FinancialAccount(
                current.id(),
                name,
                current.accountType(),
                current.initialBalance(),
                Optional.ofNullable(command.active()).orElse(current.active()),
                current.createdAt(),
                now
        );
    }
}
