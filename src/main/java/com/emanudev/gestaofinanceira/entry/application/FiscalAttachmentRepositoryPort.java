package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalAttachmentRepositoryPort {

    FiscalAttachment save(FiscalAttachment attachment);

    Optional<FiscalAttachment> findById(UUID id);

    List<FiscalAttachment> findByEntryId(UUID entryId);

    int countByEntryOrRootEntry(UUID entryId, UUID rootEntryId);
}
