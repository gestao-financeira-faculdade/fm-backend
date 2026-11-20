package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

public record CancelFinancialEntryRequest(
        @Size(max = 500) String reason,
        @Size(max = 500) String cancellationReason
) {
}
