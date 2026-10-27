package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class FinancialAccountPersistenceAdapter implements FinancialAccountRepositoryPort {

    private final SpringDataFinancialAccountRepository repository;

    FinancialAccountPersistenceAdapter(SpringDataFinancialAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public FinancialAccount save(FinancialAccount account) {
        FinancialAccountJpaEntity saved = repository.save(FinancialAccountJpaEntity.fromDomain(account));
        return saved.toDomain();
    }

    @Override
    public Optional<FinancialAccount> findById(UUID id) {
        return repository.findById(id).map(FinancialAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<FinancialAccount> findByNameIgnoreCase(String name) {
        return repository.findByNameIgnoreCase(name).map(FinancialAccountJpaEntity::toDomain);
    }

    @Override
    public List<FinancialAccount> findAllActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream()
                .map(FinancialAccountJpaEntity::toDomain)
                .toList();
    }
}
