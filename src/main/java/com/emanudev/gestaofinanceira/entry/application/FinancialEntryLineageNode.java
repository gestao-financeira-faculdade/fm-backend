package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntryLineageNode(
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
        String currency,
        Instant createdAt
) {
}
