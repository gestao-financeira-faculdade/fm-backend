package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.time.LocalDate;

public record SettlementCommand(
        Money settledPrincipalAmount,
        Money discountAmount,
        Money interestAmount,
        Money lateFeeAmount,
        LocalDate settlementDate,
        boolean createResidualEntryOnUnderpayment
) {

    public SettlementCommand {
        if (settledPrincipalAmount == null) {
            throw new BusinessRuleViolationException("INVALID_SETTLEMENT_AMOUNT", "Settled principal amount must not be null");
        }
        if (settlementDate == null) {
            throw new BusinessRuleViolationException("INVALID_SETTLEMENT_DATE", "Settlement date must not be null");
        }
        String currency = settledPrincipalAmount.currency();
        discountAmount = discountAmount != null ? discountAmount : Money.zero(currency);
        interestAmount = interestAmount != null ? interestAmount : Money.zero(currency);
        lateFeeAmount = lateFeeAmount != null ? lateFeeAmount : Money.zero(currency);

        if (discountAmount.isNegative()) {
            throw new BusinessRuleViolationException("INVALID_DISCOUNT_AMOUNT", "Discount amount cannot be negative");
        }
        if (interestAmount.isNegative()) {
            throw new BusinessRuleViolationException("INVALID_INTEREST_AMOUNT", "Interest amount cannot be negative");
        }
        if (lateFeeAmount.isNegative()) {
            throw new BusinessRuleViolationException("INVALID_LATE_FEE_AMOUNT", "Late fee amount cannot be negative");
        }
    }

    public static SettlementCommand full(Money principalAmount, LocalDate settlementDate) {
        String currency = principalAmount != null ? principalAmount.currency() : Money.DEFAULT_CURRENCY;
        return new SettlementCommand(
                principalAmount,
                Money.zero(currency),
                Money.zero(currency),
                Money.zero(currency),
                settlementDate,
                false
        );
    }

    public static SettlementCommand partialWithSplit(Money settledPrincipalAmount, LocalDate settlementDate) {
        String currency = settledPrincipalAmount != null ? settledPrincipalAmount.currency() : Money.DEFAULT_CURRENCY;
        return new SettlementCommand(
                settledPrincipalAmount,
                Money.zero(currency),
                Money.zero(currency),
                Money.zero(currency),
                settlementDate,
                true
        );
    }
}
