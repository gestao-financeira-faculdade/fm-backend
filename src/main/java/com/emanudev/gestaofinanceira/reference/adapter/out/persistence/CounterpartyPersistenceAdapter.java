package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class CounterpartyPersistenceAdapter implements CounterpartyRepositoryPort {

    private final SpringDataCounterpartyRepository repository;

    CounterpartyPersistenceAdapter(SpringDataCounterpartyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Counterparty save(Counterparty counterparty) {
        CounterpartyJpaEntity saved = repository.save(CounterpartyJpaEntity.fromDomain(counterparty));
        return saved.toDomain();
    }

    @Override
    public Optional<Counterparty> findById(UUID id) {
        return repository.findById(id).map(CounterpartyJpaEntity::toDomain);
    }

    @Override
    public Optional<Counterparty> findByTaxId(String taxId) {
        return repository.findByTaxId(taxId).map(CounterpartyJpaEntity::toDomain);
    }

    @Override
    public List<Counterparty> findAllActive() {
        return repository.findByActiveTrueOrderByLegalNameAsc().stream()
                .map(CounterpartyJpaEntity::toDomain)
                .toList();
    }
}
