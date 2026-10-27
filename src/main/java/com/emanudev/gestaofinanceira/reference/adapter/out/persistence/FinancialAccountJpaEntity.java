package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_accounts")
public class FinancialAccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal initialBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialAccountJpaEntity() {
    }

    public static FinancialAccountJpaEntity fromDomain(FinancialAccount domain) {
        FinancialAccountJpaEntity entity = new FinancialAccountJpaEntity();
        entity.id = domain.id();
        entity.name = domain.name();
        entity.accountType = domain.accountType();
        entity.initialBalance = domain.initialBalance().amount();
        entity.currency = domain.initialBalance().currency();
        entity.active = domain.active();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    public FinancialAccount toDomain() {
        return new FinancialAccount(
                id,
                name,
                accountType,
                Money.of(initialBalance, currency),
                active,
                createdAt,
                updatedAt
        );
    }
}
