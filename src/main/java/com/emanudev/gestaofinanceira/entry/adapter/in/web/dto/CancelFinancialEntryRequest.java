package com.emanudev.gestaofinanceira.entry.adapter.in.web.dto;

public record CancelFinancialEntryRequest(
        String reason,
        String cancellationReason
) {
}
