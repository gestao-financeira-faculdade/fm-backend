package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListFinancialAccountsUseCase {

    private final FinancialAccountRepositoryPort accountRepository;

    public ListFinancialAccountsUseCase(FinancialAccountRepositoryPort accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<FinancialAccount> execute() {
        return accountRepository.findAllActive();
    }
}
