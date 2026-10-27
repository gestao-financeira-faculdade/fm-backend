package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntryResponse(
        UUID id,
        String idempotencyKey,
        UUID parentEntryId,
        UUID rootEntryId,
        UUID accountId,
        String accountName,
        UUID categoryId,
        String categoryName,
        boolean categoryRequiresAttachment,
        UUID counterpartyId,
        String counterpartyName,
        EntryType entryType,
        EntryStatus status,
        String description,
        LocalDate competenceDate,
        LocalDate dueDate,
        LocalDate settlementDate,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal discountAmount,
        BigDecimal interestAmount,
        BigDecimal lateFeeAmount,
        String currency,
        int attachmentCount,
        String cancellationReason,
        Instant cancelledAt,
        UUID cancelledBy,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static FinancialEntryResponse from(FinancialEntry entry, FinancialEntrySummaryView summary) {
        return new FinancialEntryResponse(
                entry.id(),
                entry.idempotencyKey(),
                entry.parentEntryId(),
                entry.rootEntryId(),
                entry.accountId(),
                summary != null ? summary.accountName() : null,
                entry.categoryId(),
                summary != null ? summary.categoryName() : null,
                summary != null && summary.categoryRequiresAttachment(),
                entry.counterpartyId(),
                summary != null ? summary.counterpartyName() : null,
                entry.entryType(),
                entry.status(),
                entry.description(),
                entry.competenceDate(),
                entry.dueDate(),
                entry.settlementDate(),
                entry.originalAmount().amount(),
                entry.settledAmount() != null ? entry.settledAmount().amount() : null,
                entry.discountAmount().amount(),
                entry.interestAmount().amount(),
                entry.lateFeeAmount().amount(),
                entry.originalAmount().currency(),
                summary != null ? summary.attachmentCount() : 0,
                entry.cancellationReason(),
                entry.cancelledAt(),
                entry.cancelledBy(),
                entry.createdBy(),
                entry.version(),
                entry.createdAt(),
                entry.updatedAt()
        );
    }
}
