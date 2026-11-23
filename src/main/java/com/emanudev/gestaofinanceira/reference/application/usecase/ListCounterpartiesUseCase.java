package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListCounterpartiesUseCase {

    private final CounterpartyRepositoryPort counterpartyRepository;

    public ListCounterpartiesUseCase(CounterpartyRepositoryPort counterpartyRepository) {
        this.counterpartyRepository = counterpartyRepository;
    }

    public List<Counterparty> execute() {
        return counterpartyRepository.findAllActive();
    }

    public PagedResult<Counterparty> execute(PageQuery page, boolean includeInactive) {
        return counterpartyRepository.findPage(includeInactive, page);
    }
}
