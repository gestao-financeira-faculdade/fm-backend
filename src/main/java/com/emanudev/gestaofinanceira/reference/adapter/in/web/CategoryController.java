package com.emanudev.gestaofinanceira.reference.adapter.in.web;

import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CategoryResponse;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.CreateCategoryRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.dto.PatchCategoryRequest;
import com.emanudev.gestaofinanceira.reference.adapter.in.web.mapper.ReferenceDataWebMapper;
import com.emanudev.gestaofinanceira.reference.application.PatchCategoryCommand;
import com.emanudev.gestaofinanceira.reference.application.usecase.CreateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.GetCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.ListCategoriesUseCase;
import com.emanudev.gestaofinanceira.reference.application.usecase.UpdateCategoryUseCase;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class CategoryController implements CategoryControllerOpenApi {

    private final CreateCategoryUseCase createCategoryUseCase;
    private final ListCategoriesUseCase listCategoriesUseCase;
    private final GetCategoryUseCase getCategoryUseCase;
    private final UpdateCategoryUseCase updateCategoryUseCase;
    private final ReferenceDataWebMapper referenceDataWebMapper;

    @Autowired
    public CategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            ListCategoriesUseCase listCategoriesUseCase,
            GetCategoryUseCase getCategoryUseCase,
            UpdateCategoryUseCase updateCategoryUseCase,
            ReferenceDataWebMapper referenceDataWebMapper
    ) {
        this.createCategoryUseCase = createCategoryUseCase;
        this.listCategoriesUseCase = listCategoriesUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
        this.updateCategoryUseCase = updateCategoryUseCase;
        this.referenceDataWebMapper = referenceDataWebMapper;
    }

    public CategoryController(
            CreateCategoryUseCase createCategoryUseCase,
            ListCategoriesUseCase listCategoriesUseCase,
            GetCategoryUseCase getCategoryUseCase,
            UpdateCategoryUseCase updateCategoryUseCase
    ) {
        this(createCategoryUseCase, listCategoriesUseCase, getCategoryUseCase, updateCategoryUseCase, new ReferenceDataWebMapper());
    }

    @Override
    public ResponseEntity<PagedResult<CategoryResponse>> listCategories(int page, int size, boolean includeInactive) {
        PagedResult<Category> result = listCategoriesUseCase.execute(PageQuery.of(page, size), includeInactive);
        return ResponseEntity.ok(PagedResult.of(
                referenceDataWebMapper.toCategoryResponseList(result.items()),
                result.totalElements(),
                result.page(),
                result.size()
        ));
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
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(referenceDataWebMapper.toCategoryResponse(created));
    }

    @Override
    public ResponseEntity<CategoryResponse> patchCategory(UUID id, PatchCategoryRequest request) {
        Category updated = updateCategoryUseCase.patch(id, new PatchCategoryCommand(request.name(), request.requiresAttachmentAlways(), request.fixedCost(), request.active()));
        return ResponseEntity.ok(referenceDataWebMapper.toCategoryResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deactivateCategory(UUID id) {
        updateCategoryUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
