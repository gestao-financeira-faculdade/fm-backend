package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetFinancialAccountUseCase {

    private final FinancialAccountRepositoryPort accountRepository;

    public GetFinancialAccountUseCase(FinancialAccountRepositoryPort accountRepository) {
        this.accountRepository = accountRepository;
    }

    public FinancialAccount execute(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialAccount", id));
    }
}
