package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntrySummaryView(
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
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
