package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataFinancialEntryRepository extends JpaRepository<FinancialEntryJpaEntity, UUID> {

    Optional<FinancialEntryJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<FinancialEntryJpaEntity> findByStatusAndDueDateBeforeOrderByDueDateAsc(EntryStatus status, LocalDate today);

    List<FinancialEntryJpaEntity> findByParentEntryIdOrderByCreatedAtAsc(UUID parentEntryId);

    List<FinancialEntryJpaEntity> findByRootEntryIdOrderByCreatedAtAsc(UUID rootEntryId);

    boolean existsByParentEntryIdAndStatusNot(UUID parentEntryId, EntryStatus status);
}
