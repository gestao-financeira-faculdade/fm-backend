package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CreateCounterpartyUseCase {

    private final CounterpartyRepositoryPort counterpartyRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public CreateCounterpartyUseCase(
            CounterpartyRepositoryPort counterpartyRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this.counterpartyRepository = counterpartyRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public Counterparty execute(
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
}
