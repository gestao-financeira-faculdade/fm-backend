package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCategoryRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByNameIgnoreCaseAndCategoryType(String name, CategoryType categoryType);

    List<CategoryJpaEntity> findByActiveTrueOrderByNameAsc();

    Page<CategoryJpaEntity> findByActiveTrue(Pageable pageable);
}
