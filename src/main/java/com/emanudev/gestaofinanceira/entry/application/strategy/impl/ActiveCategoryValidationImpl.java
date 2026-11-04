package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class ActiveCategoryValidationImpl implements CreateEntryValidationStrategy {

    @Override
    public void execute(CreateEntryValidationContext context) {
        if (!context.category().active()) {
            throw new BusinessRuleViolationException(
                    "INACTIVE_CATEGORY",
                    "Category is inactive: " + context.category().id()
            );
        }
    }
}
