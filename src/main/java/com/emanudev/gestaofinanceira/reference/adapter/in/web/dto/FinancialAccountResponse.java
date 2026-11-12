package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialAccountResponse(
        UUID id,
        String name,
        AccountType accountType,
        BigDecimal initialBalance,
        String currency,
        boolean active,
        Instant createdAt
) {
    public static FinancialAccountResponse fromDomain(FinancialAccount account) {
        return new FinancialAccountResponse(
                account.id(),
                account.name(),
                account.accountType(),
                account.initialBalance().amount(),
                account.initialBalance().currency(),
                account.active(),
                account.createdAt()
        );
    }
}
