package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.AttachmentStoragePort;
import com.emanudev.gestaofinanceira.entry.application.DownloadedAttachment;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.FiscalAttachmentRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DownloadFiscalAttachmentUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FiscalAttachmentRepositoryPort attachmentRepository;
    private final AttachmentStoragePort attachmentStoragePort;

    public DownloadFiscalAttachmentUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository,
            AttachmentStoragePort attachmentStoragePort
    ) {
        this.entryRepository = entryRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStoragePort = attachmentStoragePort;
    }

    public DownloadedAttachment execute(UUID entryId, UUID attachmentId) {
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
}
