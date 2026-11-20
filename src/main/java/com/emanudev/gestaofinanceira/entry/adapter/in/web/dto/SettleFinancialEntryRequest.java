package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SettleFinancialEntryRequest(
        @NotNull BigDecimal paidAmount,
        BigDecimal discountAmount,
        BigDecimal interestAmount,
        BigDecimal lateFeeAmount,
        LocalDate settlementDate,
        Boolean allowPartial,
        @NotNull Long expectedVersion
) {
}
