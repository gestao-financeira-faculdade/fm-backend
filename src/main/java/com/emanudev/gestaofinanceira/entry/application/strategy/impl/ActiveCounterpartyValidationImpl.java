package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationStrategy;
import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class ActiveCounterpartyValidationImpl implements CreateEntryValidationStrategy {

    private final CounterpartyRepositoryPort counterpartyRepository;

    public ActiveCounterpartyValidationImpl(CounterpartyRepositoryPort counterpartyRepository) {
        this.counterpartyRepository = counterpartyRepository;
    }

    @Override
    public void execute(CreateEntryValidationContext context) {
        var counterpartyId = context.command().counterpartyId();
        if (counterpartyId != null) {
            Counterparty counterparty = counterpartyRepository.findById(counterpartyId)
                    .orElseThrow(() -> new BusinessRuleViolationException("COUNTERPARTY_NOT_FOUND", "Counterparty not found: " + counterpartyId));
            if (!counterparty.active()) {
                throw new BusinessRuleViolationException(
                        "INACTIVE_COUNTERPARTY",
                        "Counterparty is inactive: " + counterparty.id()
                );
            }
        }
    }
}
