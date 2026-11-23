package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
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

    @Override
    public PagedResult<FinancialAccount> findPage(boolean includeInactive, PageQuery page) {
        PageRequest request = PageRequest.of(page.page(), page.size(), Sort.by("name").and(Sort.by("id")));
        Page<FinancialAccountJpaEntity> result = includeInactive ? repository.findAll(request) : repository.findByActiveTrue(request);
        return PagedResult.of(
                result.map(FinancialAccountJpaEntity::toDomain).getContent(),
                result.getTotalElements(),
                page.page(),
                page.size()
        );
    }
}
