package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepository;

    public ListCategoriesUseCase(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> execute() {
        return categoryRepository.findAllActive();
    }

    public PagedResult<Category> execute(PageQuery page, boolean includeInactive) {
        return categoryRepository.findPage(includeInactive, page);
    }
}
