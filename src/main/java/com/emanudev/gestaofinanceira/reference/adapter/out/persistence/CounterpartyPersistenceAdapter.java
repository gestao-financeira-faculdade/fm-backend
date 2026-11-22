package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

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

    @Override
    public PagedResult<Counterparty> findPage(boolean includeInactive, PageQuery page) {
        PageRequest request = PageRequest.of(page.page(), page.size(), Sort.by("legalName").and(Sort.by("id")));
        Page<CounterpartyJpaEntity> result = includeInactive ? repository.findAll(request) : repository.findByActiveTrue(request);
        return PagedResult.of(
                result.map(CounterpartyJpaEntity::toDomain).getContent(),
                result.getTotalElements(),
                page.page(),
                page.size()
        );
    }
}
