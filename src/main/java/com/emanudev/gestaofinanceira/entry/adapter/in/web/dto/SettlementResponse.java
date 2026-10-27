package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID parentEntryId,
        UUID rootEntryId,
        EntryType entryType,
        EntryStatus status,
        String description,
        LocalDate dueDate,
        LocalDate settlementDate,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        BigDecimal discountAmount,
        BigDecimal interestAmount,
        BigDecimal lateFeeAmount,
        String currency,
        long version,
        boolean hasResidualChild,
        FinancialEntryResponse settledEntry,
        FinancialEntryResponse residualChildEntry
) {
    public static SettlementResponse from(
            FinancialEntryResponse settledEntry,
            FinancialEntryResponse residualChildEntry
    ) {
        return new SettlementResponse(
                settledEntry.id(),
                settledEntry.parentEntryId(),
                settledEntry.rootEntryId(),
                settledEntry.entryType(),
                settledEntry.status(),
                settledEntry.description(),
                settledEntry.dueDate(),
                settledEntry.settlementDate(),
                settledEntry.originalAmount(),
                settledEntry.settledAmount(),
                settledEntry.discountAmount(),
                settledEntry.interestAmount(),
                settledEntry.lateFeeAmount(),
                settledEntry.currency(),
                settledEntry.version(),
                residualChildEntry != null,
                settledEntry,
                residualChildEntry
        );
    }
}
