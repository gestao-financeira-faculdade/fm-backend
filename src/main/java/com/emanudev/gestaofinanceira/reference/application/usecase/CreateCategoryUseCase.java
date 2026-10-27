package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CreateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public CreateCategoryUseCase(
            CategoryRepositoryPort categoryRepository,
            AuditTrailPort auditTrailPort,
            Clock clock
    ) {
        this.categoryRepository = categoryRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public Category execute(
            String name,
            CategoryType categoryType,
            boolean requiresAttachmentAlways,
            boolean fixedCost
    ) {
        categoryRepository.findByNameAndType(name.trim(), categoryType).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                    "DUPLICATE_CATEGORY",
                    "Category already exists with name '%s' and type '%s'"
                            .formatted(existing.name(), existing.categoryType())
            );
        });
        Instant now = Instant.now(clock);
        Category created = categoryRepository.save(
                Category.create(name, categoryType, requiresAttachmentAlways, fixedCost, now)
        );
        auditTrailPort.recordTransition("CATEGORY", created.id(), AuditAction.CREATE, null, created);
        return created;
    }
}
