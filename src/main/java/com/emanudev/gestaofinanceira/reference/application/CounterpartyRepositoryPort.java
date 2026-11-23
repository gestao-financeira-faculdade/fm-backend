package com.emanudev.gestaofinanceira.reference.application;

import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CounterpartyRepositoryPort {

    Counterparty save(Counterparty counterparty);

    Optional<Counterparty> findById(UUID id);

    Optional<Counterparty> findByTaxId(String taxId);

    List<Counterparty> findAllActive();

    PagedResult<Counterparty> findPage(boolean includeInactive, PageQuery page);
}
