package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCounterpartyRepository extends JpaRepository<CounterpartyJpaEntity, UUID> {

    Optional<CounterpartyJpaEntity> findByTaxId(String taxId);

    List<CounterpartyJpaEntity> findByActiveTrueOrderByLegalNameAsc();
}
