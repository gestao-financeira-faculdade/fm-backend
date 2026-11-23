package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record PatchCounterpartyRequest(
        @Size(max = 180) String legalName,
        @Size(max = 180) String tradeName,
        Boolean active
) {
}
