package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFinancialAccountRepository extends JpaRepository<FinancialAccountJpaEntity, UUID> {

    Optional<FinancialAccountJpaEntity> findByNameIgnoreCase(String name);

    List<FinancialAccountJpaEntity> findByActiveTrueOrderByNameAsc();
}
