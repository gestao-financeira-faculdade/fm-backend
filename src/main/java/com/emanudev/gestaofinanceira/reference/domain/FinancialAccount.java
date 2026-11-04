package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.time.Instant;
import java.util.UUID;

public record FinancialAccount(
        UUID id,
        String name,
        AccountType accountType,
        Money initialBalance,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public FinancialAccount {
        if (id == null) {
            throw new BusinessRuleViolationException("INVALID_ACCOUNT_ID", "Account id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_ACCOUNT_NAME", "Account name must not be blank");
        }
        if (accountType == null) {
            throw new BusinessRuleViolationException("INVALID_ACCOUNT_TYPE", "Account type must not be null");
        }
        if (initialBalance == null) {
            throw new BusinessRuleViolationException("INVALID_INITIAL_BALANCE", "Initial balance must not be null");
        }
        name = name.trim();
    }

    public static FinancialAccount create(
            String name,
            AccountType accountType,
            Money initialBalance,
            Instant now
    ) {
        Instant timestamp = now != null ? now : Instant.now();
        return new FinancialAccount(
                UUID.randomUUID(),
                name,
                accountType,
                initialBalance,
                true,
                timestamp,
                timestamp
        );
    }
}
