package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;

import java.time.Instant;
import java.util.UUID;

public record Category(
        UUID id,
        String name,
        CategoryType categoryType,
        boolean requiresAttachmentAlways,
        boolean fixedCost,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public Category {
        if (id == null) {
            throw new BusinessRuleViolationException("INVALID_CATEGORY_ID", "Category id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_CATEGORY_NAME", "Category name must not be blank");
        }
        if (categoryType == null) {
            throw new BusinessRuleViolationException("INVALID_CATEGORY_TYPE", "Category type must not be null");
        }
        name = name.trim();
        if ("impostos".equalsIgnoreCase(name)) {
            requiresAttachmentAlways = true;
        }
    }

    public static Category create(
            String name,
            CategoryType categoryType,
            boolean requiresAttachmentAlways,
            boolean fixedCost,
            Instant now
    ) {
        Instant timestamp = now != null ? now : Instant.now();
        return new Category(
                UUID.randomUUID(),
                name,
                categoryType,
                requiresAttachmentAlways,
                fixedCost,
                true,
                timestamp,
                timestamp
        );
    }

    public boolean requiresMandatoryFiscalAttachment() {
        return requiresAttachmentAlways || "impostos".equalsIgnoreCase(name);
    }
}
