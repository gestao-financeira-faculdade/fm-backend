package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PagedResult<FinancialAccount> execute(PageQuery page, boolean includeInactive) {
        return accountRepository.findPage(includeInactive, page);
    }
}
