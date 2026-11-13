package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 20)
    private CategoryType categoryType;

    @Column(name = "requires_attachment_always", nullable = false)
    private boolean requiresAttachmentAlways;

    @Column(name = "is_fixed_cost", nullable = false)
    private boolean fixedCost;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryJpaEntity() {
    }

    public static CategoryJpaEntity fromDomain(Category domain) {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        entity.id = domain.id();
        entity.name = domain.name();
        entity.categoryType = domain.categoryType();
        entity.requiresAttachmentAlways = domain.requiresAttachmentAlways();
        entity.fixedCost = domain.fixedCost();
        entity.active = domain.active();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    public Category toDomain() {
        return new Category(
                id,
                name,
                categoryType,
                requiresAttachmentAlways,
                fixedCost,
                active,
                createdAt,
                updatedAt
        );
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryJpaEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
