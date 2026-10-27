package com.emanudev.gestaofinanceira.reference.application;

import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialAccountRepositoryPort {

    FinancialAccount save(FinancialAccount account);

    Optional<FinancialAccount> findById(UUID id);

    Optional<FinancialAccount> findByNameIgnoreCase(String name);

    List<FinancialAccount> findAllActive();
}
