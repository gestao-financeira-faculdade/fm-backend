package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController implements CategoryControllerOpenApi {

    private final ReferenceDataService referenceDataService;

    public CategoryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Override
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        List<CategoryResponse> categories = referenceDataService.listCategories().stream()
                .map(CategoryResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategory(UUID id) {
        return ResponseEntity.ok(CategoryResponse.fromDomain(referenceDataService.getCategoryById(id)));
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(CreateCategoryRequest request) {
        Category created = referenceDataService.createCategory(
                request.name(),
                request.categoryType(),
                Boolean.TRUE.equals(request.requiresAttachmentAlways()),
                Boolean.TRUE.equals(request.fixedCost())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.fromDomain(created));
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull CategoryType categoryType,
            Boolean requiresAttachmentAlways,
            Boolean fixedCost
    ) {
    }

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
}
