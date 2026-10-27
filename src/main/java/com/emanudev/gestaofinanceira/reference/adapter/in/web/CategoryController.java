package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCategoryRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCategoriesUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController implements CategoryControllerOpenApi {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;
    private final GetCategoryUseCase getCategoryUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public CategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            ListCategoriesUseCase listCategoriesUseCase,
            GetCategoryUseCase getCategoryUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createCategoryUseCase = createCategoryUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public CategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            ListCategoriesUseCase listCategoriesUseCase,
            GetCategoryUseCase getCategoryUseCase
    ) {
        this(createCategoryUseCase, listCategoriesUseCase, getCategoryUseCase, new ReferenceDataWebMapper());
    }

    @Override
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(
                referenceDataWebMapper.toCategoryResponseList(listCategoriesUseCase.execute())
        );
    }

    @Override
    public ResponseEntity<CategoryResponse> getCategory(UUID id) {
        return ResponseEntity.ok(
                referenceDataWebMapper.toCategoryResponse(getCategoryUseCase.execute(id))
        );
    }

    @Override
    public ResponseEntity<CategoryResponse> createCategory(CreateCategoryRequest request) {
        Category created = createCategoryUseCase.execute(
                request.name(),
                request.categoryType(),
                referenceDataWebMapper.isRequiresAttachmentAlways(request),
                referenceDataWebMapper.isFixedCost(request)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referenceDataWebMapper.toCategoryResponse(created));
    }
}
