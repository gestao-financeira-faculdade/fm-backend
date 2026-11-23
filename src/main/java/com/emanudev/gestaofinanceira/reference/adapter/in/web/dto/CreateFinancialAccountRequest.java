package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateFinancialAccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType accountType,
        @Digits(integer = 17, fraction = 2) BigDecimal initialBalance,
        @Size(min = 3, max = 3) String currency
) {
}
