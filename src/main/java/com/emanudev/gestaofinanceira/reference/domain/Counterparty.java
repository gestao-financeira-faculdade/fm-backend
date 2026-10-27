package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.time.Instant;
import java.util.UUID;

public record Counterparty(
        UUID id,
        String legalName,
        String tradeName,
        String taxId,
        CounterpartyType counterpartyType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public Counterparty {
        if (id == null) {
            throw new BusinessRuleViolationException("INVALID_COUNTERPARTY_ID", "Counterparty id must not be null");
        }
        if (legalName == null || legalName.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_COUNTERPARTY_NAME", "Counterparty legalName must not be blank");
        }
        if (counterpartyType == null) {
            throw new BusinessRuleViolationException("INVALID_COUNTERPARTY_TYPE", "Counterparty type must not be null");
        }
        legalName = legalName.trim();
        tradeName = (tradeName == null || tradeName.isBlank()) ? null : tradeName.trim();
        taxId = (taxId == null || taxId.isBlank()) ? null : taxId.trim();
    }

    public static Counterparty create(
            String legalName,
            String tradeName,
            String taxId,
            CounterpartyType counterpartyType,
            Instant now
    ) {
        Instant timestamp = now != null ? now : Instant.now();
        return new Counterparty(
                UUID.randomUUID(),
                legalName,
                tradeName,
                taxId,
                counterpartyType,
                true,
                timestamp,
                timestamp
        );
    }
}
