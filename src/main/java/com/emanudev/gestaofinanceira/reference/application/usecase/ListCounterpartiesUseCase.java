package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
