package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.application.FiscalAttachmentRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FiscalAttachmentPersistenceAdapter implements FiscalAttachmentRepositoryPort {

    private final SpringDataFiscalAttachmentRepository repository;

    public FiscalAttachmentPersistenceAdapter(SpringDataFiscalAttachmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public FiscalAttachment save(FiscalAttachment attachment) {
        FiscalAttachmentJpaEntity saved = repository.save(FiscalAttachmentJpaEntity.fromDomain(attachment));
        return saved.toDomain();
    }

    @Override
    public Optional<FiscalAttachment> findById(UUID id) {
        return repository.findById(id).map(FiscalAttachmentJpaEntity::toDomain);
    }

    @Override
    public List<FiscalAttachment> findByEntryId(UUID entryId) {
        return repository.findByEntryIdOrderByUploadedAtAsc(entryId).stream()
                .map(FiscalAttachmentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int countByEntryOrRootEntry(UUID entryId, UUID rootEntryId) {
        UUID effectiveRootId = rootEntryId != null ? rootEntryId : entryId;
        return (int) repository.countByEntryOrRootLineage(entryId, effectiveRootId);
    }
}
