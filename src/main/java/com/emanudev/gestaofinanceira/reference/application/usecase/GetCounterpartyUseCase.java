package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCounterpartyUseCase {

    private final CounterpartyRepositoryPort counterpartyRepository;

    public GetCounterpartyUseCase(CounterpartyRepositoryPort counterpartyRepository) {
        this.counterpartyRepository = counterpartyRepository;
    }

    public Counterparty execute(UUID id) {
        return counterpartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty", id));
    }
}
