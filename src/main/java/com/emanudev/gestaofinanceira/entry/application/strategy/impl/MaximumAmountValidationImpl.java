package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class MaximumAmountValidationImpl implements CreateEntryValidationStrategy {

    public static final String ERROR_CODE = "ENTRY_AMOUNT_ABOVE_LIMIT";

    private final BigDecimal maxAmount;

    public MaximumAmountValidationImpl(@Value("${app.entries.max-amount}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    @Override
    public void execute(CreateEntryValidationContext context) {
        BigDecimal amount = context.command().originalAmount().amount();
        if (amount.compareTo(maxAmount) > 0) {
            throw new BusinessRuleViolationException(
                    ERROR_CODE,
                    "Entry amount " + amount.toPlainString() + " exceeds the maximum allowed of " + maxAmount.toPlainString()
            );
        }
    }
}
