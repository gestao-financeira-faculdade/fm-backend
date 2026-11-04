package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialEntryRepositoryPort {

    FinancialEntry save(FinancialEntry entry);

    Optional<FinancialEntry> findById(UUID id);

    Optional<FinancialEntry> findByIdempotencyKey(String idempotencyKey);

    List<FinancialEntry> findOverdueCandidates(LocalDate today);

    List<FinancialEntry> findByParentEntryId(UUID parentEntryId);

    List<FinancialEntry> findByRootEntryId(UUID rootEntryId);

    boolean hasActiveChildren(UUID parentEntryId);
}
