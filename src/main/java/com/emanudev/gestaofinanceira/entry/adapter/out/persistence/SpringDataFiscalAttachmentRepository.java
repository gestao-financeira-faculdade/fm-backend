package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataFiscalAttachmentRepository extends JpaRepository<FiscalAttachmentJpaEntity, UUID> {

    List<FiscalAttachmentJpaEntity> findByEntryIdOrderByUploadedAtAsc(UUID entryId);

    @Query("""
            SELECT COUNT(fa) FROM FiscalAttachmentJpaEntity fa
            WHERE fa.entryId = :entryId
               OR fa.entryId = :rootEntryId
               OR fa.entryId IN (
                   SELECT fe.id FROM FinancialEntryJpaEntity fe
                   WHERE fe.rootEntryId = :rootEntryId
               )
            """)
    long countByEntryOrRootLineage(
            @Param("entryId") UUID entryId,
            @Param("rootEntryId") UUID rootEntryId
    );
}
