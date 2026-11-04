package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryQueryPort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.application.FinancialEntrySummaryView;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetFinancialEntryUseCase {

    private final FinancialEntryRepositoryPort entryRepository;
    private final FinancialEntryQueryPort entryQueryPort;

    public GetFinancialEntryUseCase(
            FinancialEntryRepositoryPort entryRepository,
            FinancialEntryQueryPort entryQueryPort
    ) {
        this.entryRepository = entryRepository;
        this.entryQueryPort = entryQueryPort;
    }

    public FinancialEntry getById(UUID entryId) {
        return entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));
    }

    public FinancialEntrySummaryView getSummaryById(UUID entryId) {
        return entryQueryPort.findEntrySummaryById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialEntry", entryId));
    }
}
