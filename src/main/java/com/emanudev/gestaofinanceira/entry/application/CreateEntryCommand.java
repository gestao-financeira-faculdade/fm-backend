package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.shared.domain.Money;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEntryCommand(
        String idempotencyKey,
        UUID accountId,
        UUID categoryId,
        UUID counterpartyId,
        EntryType entryType,
        String description,
        LocalDate competenceDate,
        LocalDate dueDate,
        Money originalAmount
) {
}
