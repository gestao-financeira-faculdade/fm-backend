package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCategoryRequest;
import com.emanudev.gestaofinanceira.reference.application.ReferenceDataService;
import com.emanudev.gestaofinanceira.reference.domain.Category;
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
}
