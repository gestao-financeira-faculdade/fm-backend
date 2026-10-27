package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFinancialEntryRequest(
        String idempotencyKey,
        UUID accountId,
        @NotNull UUID categoryId,
        UUID counterpartyId,
        @NotNull EntryType entryType,
        @NotBlank @Size(max = 255) String description,
        LocalDate competenceDate,
        @NotNull LocalDate dueDate,
        BigDecimal originalAmount,
        BigDecimal amount,
        String currency
) {
}
