package com.emanudev.gestaofinanceira.reference.adapter.in.web.dto;

import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType categoryType,
        boolean requiresAttachmentAlways,
        boolean fixedCost,
        boolean active,
        Instant createdAt
) {
    public static CategoryResponse fromDomain(Category category) {
        return new CategoryResponse(
                category.id(),
                category.name(),
                category.categoryType(),
                category.requiresAttachmentAlways(),
                category.fixedCost(),
                category.active(),
                category.createdAt()
        );
    }
}
