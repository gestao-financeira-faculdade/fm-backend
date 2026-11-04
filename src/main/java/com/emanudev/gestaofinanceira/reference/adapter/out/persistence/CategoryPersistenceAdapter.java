package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class CategoryPersistenceAdapter implements CategoryRepositoryPort {

    private final SpringDataCategoryRepository repository;

    CategoryPersistenceAdapter(SpringDataCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity saved = repository.save(CategoryJpaEntity.fromDomain(category));
        return saved.toDomain();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id).map(CategoryJpaEntity::toDomain);
    }

    @Override
    public Optional<Category> findByNameAndType(String name, CategoryType categoryType) {
        return repository.findByNameIgnoreCaseAndCategoryType(name, categoryType)
                .map(CategoryJpaEntity::toDomain);
    }

    @Override
    public List<Category> findAllActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(CategoryJpaEntity::toDomain)
                .toList();
    }
}
