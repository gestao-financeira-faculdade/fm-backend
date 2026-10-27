package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import java.time.Instant;
import java.util.UUID;

public record CounterpartyResponse(
        UUID id,
        String legalName,
        String tradeName,
        String taxId,
        CounterpartyType counterpartyType,
        boolean active,
        Instant createdAt
) {
    public static CounterpartyResponse fromDomain(Counterparty counterparty) {
        return new CounterpartyResponse(
                counterparty.id(),
                counterparty.legalName(),
                counterparty.tradeName(),
                counterparty.taxId(),
                counterparty.counterpartyType(),
                counterparty.active(),
                counterparty.createdAt()
        );
    }
}
