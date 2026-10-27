package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final ReferenceDataService referenceDataService;

    public CategoryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        List<CategoryResponse> categories = referenceDataService.listCategories().stream()
                .map(CategoryResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(CategoryResponse.fromDomain(referenceDataService.getCategoryById(id)));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
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
