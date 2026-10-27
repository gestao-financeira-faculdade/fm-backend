package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryLineageNode;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryQueryPort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetFinancialEntryLineageUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FinancialEntryQueryPort entryQueryPort;

    public GetFinancialEntryLineageUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FinancialEntryQueryPort entryQueryPort
    ) {
        this.entryRepository = entryRepository;
        this.entryQueryPort = entryQueryPort;
    }

    public List<FinancialEntryLineageNode> execute(UUID entryId) {
        if (entryRepository.findById(entryId).isEmpty()) {
            throw new ResourceNotFoundException("FinancialEntry", entryId);
        }
        return entryQueryPort.getEntryLineage(entryId);
    }
}
