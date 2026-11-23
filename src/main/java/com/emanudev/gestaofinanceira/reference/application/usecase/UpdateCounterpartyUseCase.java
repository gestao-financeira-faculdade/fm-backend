package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.PatchCounterpartyCommand;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
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
public class UpdateCounterpartyUseCase {

    private final CounterpartyRepositoryPort counterpartyRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public UpdateCounterpartyUseCase(CounterpartyRepositoryPort counterpartyRepository, AuditTrailPort auditTrailPort, Clock clock) {
        this.counterpartyRepository = counterpartyRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public Counterparty patch(UUID id, PatchCounterpartyCommand command) {
        Counterparty current = load(id);
        Counterparty updated = counterpartyRepository.save(apply(current, command, Instant.now(clock)));
        auditTrailPort.recordTransition("COUNTERPARTY", id, AuditAction.UPDATE, current, updated);
        return updated;
    }

    @Transactional
    public void deactivate(UUID id) {
        Counterparty current = load(id);
        if (!current.active()) {
            return;
        }
        Instant now = Instant.now(clock);
        Counterparty inactive = new Counterparty(current.id(), current.legalName(), current.tradeName(), current.taxId(),
                current.counterpartyType(), false, current.createdAt(), now);
        Counterparty deactivated = counterpartyRepository.save(inactive);
        auditTrailPort.recordTransition("COUNTERPARTY", id, AuditAction.UPDATE, current, deactivated);
    }

    private Counterparty load(UUID id) {
        return counterpartyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Counterparty", id));
    }

    private Counterparty apply(Counterparty current, PatchCounterpartyCommand command, Instant now) {
        return new Counterparty(
                current.id(),
                command.legalName() != null ? command.legalName() : current.legalName(),
                command.tradeName() != null ? command.tradeName() : current.tradeName(),
                current.taxId(),
                current.counterpartyType(),
                Optional.ofNullable(command.active()).orElse(current.active()),
                current.createdAt(),
                now
        );
    }
}
