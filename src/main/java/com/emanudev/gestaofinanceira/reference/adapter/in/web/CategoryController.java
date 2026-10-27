package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCategoryRequest;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCategoriesUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController implements CategoryControllerOpenApi {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;
    private final GetCategoryUseCase getCategoryUseCase;

    public CategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            ListCategoriesUseCase listCategoriesUseCase,
            GetCategoryUseCase getCategoryUseCase
    ) {
        this.createCategoryUseCase = createCategoryUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
    }

    @Override
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        List<CategoryResponse> categories = listCategoriesUseCase.execute().stream()
                .map(CategoryResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategory(UUID id) {
        return ResponseEntity.ok(CategoryResponse.fromDomain(getCategoryUseCase.execute(id)));
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(CreateCategoryRequest request) {
        Category created = createCategoryUseCase.execute(
                request.name(),
                request.categoryType(),
                Boolean.TRUE.equals(request.requiresAttachmentAlways()),
                Boolean.TRUE.equals(request.fixedCost())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.fromDomain(created));
    }
}
