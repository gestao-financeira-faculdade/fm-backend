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
        BigDecimal currentBalance,
        String currency,
        boolean active,
        Instant createdAt
) {
    public static FinancialAccountResponse fromDomain(FinancialAccount account) {
        return fromDomain(account, account.initialBalance().amount());
    }

    public static FinancialAccountResponse fromDomain(FinancialAccount account, BigDecimal currentBalance) {
        return new FinancialAccountResponse(
                account.id(),
                account.name(),
                account.accountType(),
                account.initialBalance().amount(),
                currentBalance,
                account.initialBalance().currency(),
                account.active(),
                account.createdAt()
        );
    }
}
