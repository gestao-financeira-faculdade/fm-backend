package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.AttachmentStoragePort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.FiscalAttachmentRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachmentValidator;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.application.AuditedFinancialAction;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class UploadFiscalAttachmentUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FiscalAttachmentRepositoryPort attachmentRepository;
    private final AttachmentStoragePort attachmentStoragePort;
    private final FiscalAttachmentValidator attachmentValidator;
    private final AuditTrailPort auditTrailPort;
    private final ActorContextPort actorContextPort;
    private final Clock clock;

    @Autowired
    public UploadFiscalAttachmentUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AttachmentStoragePort attachmentStoragePort,
            FiscalAttachmentValidator attachmentValidator,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this.entryRepository = entryRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStoragePort = attachmentStoragePort;
        this.attachmentValidator = attachmentValidator;
        this.auditTrailPort = auditTrailPort;
        this.actorContextPort = actorContextPort;
        this.clock = clock;
    }

    public UploadFiscalAttachmentUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AttachmentStoragePort attachmentStoragePort,
            AuditTrailPort auditTrailPort,
            ActorContextPort actorContextPort,
            Clock clock
    ) {
        this(
                entryRepository,
                attachmentRepository,
                attachmentStoragePort,
                new FiscalAttachmentValidator(),
                auditTrailPort,
                actorContextPort,
                clock
        );
    }

    @Transactional
    @AuditedFinancialAction(action = AuditAction.UPLOAD_ATTACHMENT, entityType = "FINANCIAL_ENTRY")
    public FiscalAttachment execute(UUID entryId, String originalFileName, byte[] fileBytes) {
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

    private UUID currentActorUserId() {
        if (actorContextPort == null) {
            return null;
        }
        ActorContext actor = actorContextPort.getCurrentActor();
        return actor != null ? actor.userId() : null;
    }
}
