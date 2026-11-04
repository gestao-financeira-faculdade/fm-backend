package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettleFinancialEntryRequest(
        BigDecimal settledPrincipalAmount,
        BigDecimal settledAmount,
        BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal interestAmount,
        BigDecimal lateFeeAmount,
        LocalDate settlementDate,
        Boolean createResidualEntryOnUnderpayment,
        Long version
) {
}
