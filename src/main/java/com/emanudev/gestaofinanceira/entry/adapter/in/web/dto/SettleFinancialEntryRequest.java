package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SettleFinancialEntryRequest(
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal paidAmount,
        @Digits(integer = 17, fraction = 2) BigDecimal discountAmount,
        @Digits(integer = 17, fraction = 2) BigDecimal interestAmount,
        @Digits(integer = 17, fraction = 2) BigDecimal lateFeeAmount,
        LocalDate settlementDate,
        Boolean allowPartial,
        @NotNull Long expectedVersion
) {
}
