package com.emanudev.gestaofinanceira.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ReportRowItem(
        UUID id,
        String entryType,
        String status,
        String description,
        String categoryName,
        String accountName,
        String counterpartyName,
        LocalDate competenceDate,
        LocalDate dueDate,
        LocalDate settlementDate,
        BigDecimal originalAmount,
        BigDecimal settledAmount,
        String currency
) {

    public ReportRowItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(entryType, "entryType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(competenceDate, "competenceDate must not be null");
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        Objects.requireNonNull(originalAmount, "originalAmount must not be null");
        currency = (currency == null || currency.isBlank()) ? "BRL" : currency;
    }
}
