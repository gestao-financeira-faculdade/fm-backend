package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class CompatibleCategoryTypeValidationImpl implements CreateEntryValidationStrategy {

    @Override
    public void execute(CreateEntryValidationContext context) {
        Category category = context.category();
        var command = context.command();
        if (command.entryType() != null && !category.categoryType().supportsEntryType(command.entryType().name())) {
            throw new BusinessRuleViolationException(
                    "INCOMPATIBLE_CATEGORY_TYPE",
                    "Category '%s' (%s) is not compatible with entry type %s"
                            .formatted(category.name(), category.categoryType(), command.entryType())
            );
        }
    }
}
