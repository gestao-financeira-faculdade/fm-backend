package com.emanudev.gestaofinanceira.reference.adapter.out.persistence;

import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataPersistenceAdaptersTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private SpringDataFinancialAccountRepository accountRepository;
    @Mock
    private SpringDataCategoryRepository categoryRepository;
    @Mock
    private SpringDataCounterpartyRepository counterpartyRepository;

    private FinancialAccountPersistenceAdapter accountAdapter;
    private CategoryPersistenceAdapter categoryAdapter;
    private CounterpartyPersistenceAdapter counterpartyAdapter;

    @BeforeEach
    void setUp() {
        accountAdapter = new FinancialAccountPersistenceAdapter(accountRepository);
        categoryAdapter = new CategoryPersistenceAdapter(categoryRepository);
        counterpartyAdapter = new CounterpartyPersistenceAdapter(counterpartyRepository);
    }

    @Test
    void shouldSaveAndFindFinancialAccountRoundTrip() {
        FinancialAccount domain = FinancialAccount.create("Conta Reserva", AccountType.TREASURY_LEDGER, Money.brl("1500.00"), FIXED_INSTANT);
        FinancialAccountJpaEntity entity = FinancialAccountJpaEntity.fromDomain(domain);
        when(accountRepository.save(any())).thenReturn(entity);
        when(accountRepository.findById(domain.id())).thenReturn(Optional.of(entity));
        when(accountRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(entity));

        assertThat(accountAdapter.save(domain)).isEqualTo(domain);
        assertThat(accountAdapter.findById(domain.id())).contains(domain);
        assertThat(accountAdapter.findAllActive()).containsExactly(domain);
    }

    @Test
    void shouldSaveAndFindCategoryRoundTrip() {
        Category domain = Category.create("Vendas SaaS", CategoryType.RECEIVABLE, false, false, FIXED_INSTANT);
        CategoryJpaEntity entity = CategoryJpaEntity.fromDomain(domain);
        when(categoryRepository.save(any())).thenReturn(entity);
        when(categoryRepository.findByNameIgnoreCaseAndCategoryType("Vendas SaaS", CategoryType.RECEIVABLE))
                .thenReturn(Optional.of(entity));

        assertThat(categoryAdapter.save(domain)).isEqualTo(domain);
        assertThat(categoryAdapter.findByNameAndType("Vendas SaaS", CategoryType.RECEIVABLE)).contains(domain);
    }

    @Test
    void shouldSaveAndFindCounterpartyRoundTrip() {
        Counterparty domain = Counterparty.create("Fornecedor X", "FX", "99888777000166", CounterpartyType.OTHER, FIXED_INSTANT);
        CounterpartyJpaEntity entity = CounterpartyJpaEntity.fromDomain(domain);
        when(counterpartyRepository.save(any())).thenReturn(entity);
        when(counterpartyRepository.findByTaxId("99888777000166")).thenReturn(Optional.of(entity));

        assertThat(counterpartyAdapter.save(domain)).isEqualTo(domain);
        assertThat(counterpartyAdapter.findByTaxId("99888777000166")).contains(domain);
    }
}
