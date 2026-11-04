package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.FiscalAttachmentRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListFiscalAttachmentsUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FiscalAttachmentRepositoryPort attachmentRepository;

    public ListFiscalAttachmentsUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FiscalAttachmentRepositoryPort attachmentRepository
    ) {
        this.entryRepository = entryRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public List<FiscalAttachment> execute(UUID entryId) {
        if (entryRepository.findById(entryId).isEmpty()) {
            throw new ResourceNotFoundException("FinancialEntry", entryId);
        }
        return attachmentRepository.findByEntryId(entryId);
    }
}
