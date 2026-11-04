package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ActiveAccountValidationImpl implements CreateEntryValidationStrategy {

    @Override
    public void execute(CreateEntryValidationContext context) {
        if (!context.account().active()) {
            throw new BusinessRuleViolationException(
                    "INACTIVE_ACCOUNT",
                    "Financial account is inactive: " + context.account().id()
            );
        }
    }
}
