package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFinancialEntryRequest(
        @Size(max = 100) String idempotencyKey,
        UUID accountId,
        @NotNull UUID categoryId,
        UUID counterpartyId,
        @NotNull EntryType entryType,
        @NotBlank @Size(max = 255) String description,
        LocalDate competenceDate,
        @NotNull LocalDate dueDate,
        @Digits(integer = 17, fraction = 2) BigDecimal originalAmount,
        @Digits(integer = 17, fraction = 2) BigDecimal amount,
        String currency
) {

    @JsonIgnore
    @AssertTrue(message = "dueDate must not be before competenceDate")
    public boolean isDueDateOnOrAfterCompetenceDate() {
        return competenceDate == null || dueDate == null || !dueDate.isBefore(competenceDate);
    }
}
