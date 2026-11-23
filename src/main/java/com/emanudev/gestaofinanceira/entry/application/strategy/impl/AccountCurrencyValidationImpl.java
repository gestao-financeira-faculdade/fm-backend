package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class AccountCurrencyValidationImpl implements CreateEntryValidationStrategy {

    public static final String ERROR_CODE = "CURRENCY_MISMATCH";

    @Override
    public void execute(CreateEntryValidationContext context) {
        String entryCurrency = context.command().originalAmount().currency();
        String accountCurrency = context.account().initialBalance().currency();
        if (!entryCurrency.equals(accountCurrency)) {
            throw new BusinessRuleViolationException(
                    ERROR_CODE,
                    "Entry currency " + entryCurrency + " does not match account currency " + accountCurrency
            );
        }
    }
}
