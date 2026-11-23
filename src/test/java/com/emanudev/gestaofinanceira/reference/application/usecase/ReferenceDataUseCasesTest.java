package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.Counterparty;
import com.emanudev.gestaofinanceira.reference.domain.CounterpartyType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataUseCasesTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T15:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private FinancialAccountRepositoryPort accountRepository;
    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private CounterpartyRepositoryPort counterpartyRepository;
    @Mock
    private AuditTrailPort auditTrailPort;

    private CreateFinancialAccountUseCase createAccountUseCase;
    private ListFinancialAccountsUseCase listAccountsUseCase;
    private CreateCategoryUseCase createCategoryUseCase;
    private ListCategoriesUseCase listCategoriesUseCase;
    private CreateCounterpartyUseCase createCounterpartyUseCase;
    private ListCounterpartiesUseCase listCounterpartiesUseCase;

    @BeforeEach
    void setUp() {
        createAccountUseCase = new CreateFinancialAccountUseCase(accountRepository, auditTrailPort, FIXED_CLOCK);
        listAccountsUseCase = new ListFinancialAccountsUseCase(accountRepository);
        createCategoryUseCase = new CreateCategoryUseCase(categoryRepository, auditTrailPort, FIXED_CLOCK);
        listCategoriesUseCase = new ListCategoriesUseCase(categoryRepository);
        createCounterpartyUseCase = new CreateCounterpartyUseCase(counterpartyRepository, auditTrailPort, FIXED_CLOCK);
        listCounterpartiesUseCase = new ListCounterpartiesUseCase(counterpartyRepository);
    }

    @Test
    void shouldCreateAndListFinancialAccountsWithAuditTrail() {
        when(accountRepository.findByNameIgnoreCase("Banco Itaú")).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FinancialAccount created = createAccountUseCase.execute("Banco Itaú", AccountType.OPERATIONAL_LEDGER, Money.brl("1000.00"));
        when(accountRepository.findAllActive()).thenReturn(List.of(created));

        assertThat(listAccountsUseCase.execute()).containsExactly(created);
        verify(auditTrailPort).recordTransition(eq("FINANCIAL_ACCOUNT"), eq(created.id()), eq(AuditAction.CREATE), eq(null), eq(created));
    }

    @Test
    void shouldRejectDuplicateFinancialAccountName() {
        FinancialAccount existing = FinancialAccount.create("Caixa", AccountType.PETTY_CASH, Money.zeroBrl(), FIXED_INSTANT);
        when(accountRepository.findByNameIgnoreCase("Caixa")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> createAccountUseCase.execute("Caixa", AccountType.PETTY_CASH, Money.zeroBrl()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Financial account already exists");
    }

    @Test
    void shouldCreateAndListCategoriesWithAuditTrail() {
        when(categoryRepository.findByNameAndType("Aluguel", CategoryType.PAYABLE)).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Category created = createCategoryUseCase.execute("Aluguel", CategoryType.PAYABLE, false, true);
        when(categoryRepository.findAllActive()).thenReturn(List.of(created));

        assertThat(listCategoriesUseCase.execute()).containsExactly(created);
        verify(auditTrailPort).recordTransition(eq("CATEGORY"), eq(created.id()), eq(AuditAction.CREATE), eq(null), eq(created));
    }

    @Test
    void shouldCreateAndListCounterpartiesWithAuditTrail() {
        when(counterpartyRepository.findByTaxId("11222333000181")).thenReturn(Optional.empty());
        when(counterpartyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Counterparty created = createCounterpartyUseCase.execute(
                "Cliente Beta S.A.", "Beta", "11222333000181", CounterpartyType.CUSTOMER
        );
        when(counterpartyRepository.findAllActive()).thenReturn(List.of(created));

        assertThat(listCounterpartiesUseCase.execute()).containsExactly(created);
        verify(auditTrailPort).recordTransition(eq("COUNTERPARTY"), eq(created.id()), eq(AuditAction.CREATE), eq(null), eq(created));
    }
}
