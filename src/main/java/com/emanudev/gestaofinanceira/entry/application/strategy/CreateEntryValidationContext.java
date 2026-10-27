package com.emanudev.gestaofinanceira.entry.application.strategy;

import com.emanudev.gestaofinanceira.entry.application.CreateEntryCommand;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;

public record CreateEntryValidationContext(
        CreateEntryCommand command,
        FinancialAccount account,
        Category category
) {
}
