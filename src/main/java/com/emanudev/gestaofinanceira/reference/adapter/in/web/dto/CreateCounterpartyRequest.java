package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCounterpartyRequest(
        @NotBlank @Size(max = 180) String legalName,
        @Size(max = 180) String tradeName,
        @Size(max = 20) String taxId,
        @NotNull CounterpartyType counterpartyType
) {
}
