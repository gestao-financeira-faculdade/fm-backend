package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;

public record CreateEntryResult(FinancialEntry entry, boolean replayed) {
}
