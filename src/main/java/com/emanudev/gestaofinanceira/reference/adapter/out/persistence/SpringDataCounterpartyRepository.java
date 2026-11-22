package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCounterpartyRepository extends JpaRepository<CounterpartyJpaEntity, UUID> {

    Optional<CounterpartyJpaEntity> findByTaxId(String taxId);

    List<CounterpartyJpaEntity> findByActiveTrueOrderByLegalNameAsc();

    Page<CounterpartyJpaEntity> findByActiveTrue(Pageable pageable);
}
