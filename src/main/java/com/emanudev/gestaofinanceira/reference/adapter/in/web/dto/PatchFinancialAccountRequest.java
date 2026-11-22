package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record PatchFinancialAccountRequest(
        @Size(max = 120) String name,
        Boolean active
) {
}
