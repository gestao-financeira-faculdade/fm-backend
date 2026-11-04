package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;

    public GetCategoryUseCase(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category execute(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
