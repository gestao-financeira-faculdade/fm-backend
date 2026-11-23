package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.PatchCategoryCommand;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final AuditTrailPort auditTrailPort;
    private final Clock clock;

    public UpdateCategoryUseCase(CategoryRepositoryPort categoryRepository, AuditTrailPort auditTrailPort, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.auditTrailPort = auditTrailPort;
        this.clock = clock;
    }

    @Transactional
    public Category patch(UUID id, PatchCategoryCommand command) {
        Category current = load(id);
        Category updated = categoryRepository.save(apply(current, command, Instant.now(clock)));
        auditTrailPort.recordTransition("CATEGORY", id, AuditAction.UPDATE, current, updated);
        return updated;
    }

    @Transactional
    public void deactivate(UUID id) {
        Category current = load(id);
        if (!current.active()) {
            return;
        }
        Instant now = Instant.now(clock);
        Category inactive = new Category(current.id(), current.name(), current.categoryType(), current.requiresAttachmentAlways(),
                current.fixedCost(), false, current.createdAt(), now);
        Category deactivated = categoryRepository.save(inactive);
        auditTrailPort.recordTransition("CATEGORY", id, AuditAction.UPDATE, current, deactivated);
    }

    private Category load(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private Category apply(Category current, PatchCategoryCommand command, Instant now) {
        String name = command.name() != null ? command.name().trim() : current.name();
        if (command.name() != null) {
            categoryRepository.findByNameAndType(name, current.categoryType())
                    .filter(other -> !other.id().equals(current.id()))
                    .ifPresent(other -> {
                        throw new BusinessRuleViolationException(
                                "DUPLICATE_CATEGORY_NAME",
                                "Category already exists with name: " + other.name()
                        );
                    });
        }
        return new Category(
                current.id(),
                name,
                current.categoryType(),
                Optional.ofNullable(command.requiresAttachmentAlways()).orElse(current.requiresAttachmentAlways()),
                Optional.ofNullable(command.fixedCost()).orElse(current.fixedCost()),
                Optional.ofNullable(command.active()).orElse(current.active()),
                current.createdAt(),
                now
        );
    }
}
