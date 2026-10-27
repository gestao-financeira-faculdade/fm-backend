package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class CancelFinancialEntryUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final AuditTrailPort auditTrailPort;
    private final ActorContextPort actorContextPort;
    private final Clock clock;

    public CancelFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
        this.auditTrailPort = auditTrailPort;
        this.actorContextPort = actorContextPort;
        this.clock = clock;
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.CANCEL_SOFT_DELETE, entityType = "FINANCIAL_ENTRY")
    public FinancialEntry execute(UUID entryId, String reason) {
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

    private UUID currentActorUserId() {
        if (actorContextPort == null) {
            return null;
        }
        ActorContext actor = actorContextPort.getCurrentActor();
        return actor != null ? actor.userId() : null;
    }
}
