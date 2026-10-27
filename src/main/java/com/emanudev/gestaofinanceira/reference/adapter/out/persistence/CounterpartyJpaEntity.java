package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "counterparties")
public class CounterpartyJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 180)
    private String legalName;

    @Column(name = "trade_name", length = 180)
    private String tradeName;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "counterparty_type", nullable = false, length = 20)
    private CounterpartyType counterpartyType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CounterpartyJpaEntity() {
    }

    public static CounterpartyJpaEntity fromDomain(Counterparty domain) {
        CounterpartyJpaEntity entity = new CounterpartyJpaEntity();
        entity.id = domain.id();
        entity.legalName = domain.legalName();
        entity.tradeName = domain.tradeName();
        entity.taxId = domain.taxId();
        entity.counterpartyType = domain.counterpartyType();
        entity.active = domain.active();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    public Counterparty toDomain() {
        return new Counterparty(
                id,
                legalName,
                tradeName,
                taxId,
                counterpartyType,
                active,
                createdAt,
                updatedAt
        );
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CounterpartyJpaEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
