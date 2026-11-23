package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFinancialAccountRepository extends JpaRepository<FinancialAccountJpaEntity, UUID> {

    Optional<FinancialAccountJpaEntity> findByNameIgnoreCase(String name);

    List<FinancialAccountJpaEntity> findByActiveTrueOrderByNameAsc();

    Page<FinancialAccountJpaEntity> findByActiveTrue(Pageable pageable);
}
