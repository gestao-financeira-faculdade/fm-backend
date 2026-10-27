package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;

import java.time.LocalDate;
import java.util.UUID;

public record FinancialEntryFilter(
        EntryType entryType,
        EntryStatus status,
        UUID categoryId,
        UUID accountId,
        LocalDate dueDateFrom,
        LocalDate dueDateTo,
        String search
) {

    public static FinancialEntryFilter empty() {
        return new FinancialEntryFilter(null, null, null, null, null, null, null);
    }
}
