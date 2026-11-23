package com.emanudev.gestaofinanceira.reference.application;

import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepositoryPort {

    Category save(Category category);

    Optional<Category> findById(UUID id);

    Optional<Category> findByNameAndType(String name, CategoryType categoryType);

    List<Category> findAllActive();

    PagedResult<Category> findPage(boolean includeInactive, PageQuery page);
}
