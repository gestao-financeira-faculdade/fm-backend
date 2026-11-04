package com.emanudev.gestaofinanceira.entry.adapter.out.persistence;

import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "financial_entries")
public class FinancialEntryJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "parent_entry_id")
    private UUID parentEntryId;

    @Column(name = "root_entry_id")
    private UUID rootEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EntryStatus status;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "competence_date", nullable = false)
    private LocalDate competenceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "settled_amount", precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "late_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    protected FinancialEntryJpaEntity() {
    }

    public static FinancialEntryJpaEntity fromDomain(FinancialEntry domain) {
        FinancialEntryJpaEntity entity = new FinancialEntryJpaEntity();
        entity.id = domain.id();
        entity.idempotencyKey = domain.idempotencyKey();
        entity.parentEntryId = domain.parentEntryId();
        entity.rootEntryId = domain.rootEntryId();
        entity.accountId = domain.accountId();
        entity.categoryId = domain.categoryId();
        entity.counterpartyId = domain.counterpartyId();
        entity.entryType = domain.entryType();
        entity.status = domain.status();
        entity.description = domain.description();
        entity.competenceDate = domain.competenceDate();
        entity.dueDate = domain.dueDate();
        entity.settlementDate = domain.settlementDate();
        entity.originalAmount = domain.originalAmount().amount();
        entity.settledAmount = domain.settledAmount() != null ? domain.settledAmount().amount() : null;
        entity.discountAmount = domain.discountAmount().amount();
        entity.interestAmount = domain.interestAmount().amount();
        entity.lateFeeAmount = domain.lateFeeAmount().amount();
        entity.currency = domain.originalAmount().currency();
        entity.cancellationReason = domain.cancellationReason();
        entity.cancelledAt = domain.cancelledAt();
        entity.cancelledBy = domain.cancelledBy();
        entity.createdBy = domain.createdBy();
        entity.version = domain.version();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        entity.newEntity = domain.version() == 0L;
        return entity;
    }

    public FinancialEntry toDomain() {
        return new FinancialEntry(
                id,
                idempotencyKey,
                parentEntryId,
                rootEntryId,
                accountId,
                categoryId,
                counterpartyId,
                entryType,
                status,
                description,
                competenceDate,
                dueDate,
                settlementDate,
                Money.of(originalAmount, currency),
                settledAmount != null ? Money.of(settledAmount, currency) : null,
                Money.of(discountAmount, currency),
                Money.of(interestAmount, currency),
                Money.of(lateFeeAmount, currency),
                cancellationReason,
                cancelledAt,
                cancelledBy,
                createdBy,
                version,
                createdAt,
                updatedAt
        );
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    public void markNotNew() {
        this.newEntity = false;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialEntryJpaEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
