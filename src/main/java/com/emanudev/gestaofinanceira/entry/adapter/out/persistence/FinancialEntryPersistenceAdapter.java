package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FinancialEntryPersistenceAdapter implements FinancialEntryRepositoryPort {

    private final SpringDataFinancialEntryRepository repository;

    public FinancialEntryPersistenceAdapter(SpringDataFinancialEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public FinancialEntry save(FinancialEntry entry) {
        FinancialEntryJpaEntity entity = FinancialEntryJpaEntity.fromDomain(entry);
        if (repository.existsById(entry.id())) {
            entity.markNotNew();
        }
        FinancialEntryJpaEntity saved = repository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<FinancialEntry> findById(UUID id) {
        return repository.findById(id).map(FinancialEntryJpaEntity::toDomain);
    }

    @Override
    public Optional<FinancialEntry> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(idempotencyKey.trim())
                .map(FinancialEntryJpaEntity::toDomain);
    }

    @Override
    public List<FinancialEntry> findOverdueCandidates(LocalDate today) {
        return repository.findByStatusAndDueDateBeforeOrderByDueDateAsc(EntryStatus.PENDING, today).stream()
                .map(FinancialEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<FinancialEntry> findByParentEntryId(UUID parentEntryId) {
        return repository.findByParentEntryIdOrderByCreatedAtAsc(parentEntryId).stream()
                .map(FinancialEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<FinancialEntry> findByRootEntryId(UUID rootEntryId) {
        return repository.findByRootEntryIdOrderByCreatedAtAsc(rootEntryId).stream()
                .map(FinancialEntryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean hasActiveChildren(UUID parentEntryId) {
        return repository.existsByParentEntryIdAndStatusNot(parentEntryId, EntryStatus.CANCELLED);
    }
}
