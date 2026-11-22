package com.emanudev.gestaofinanceira.reference.application.usecase;

import com.emanudev.gestaofinanceira.reference.application.AccountBalanceQueryPort;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountBalancesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Mock
    private AccountBalanceQueryPort accountBalanceQueryPort;

    @Test
    void shouldAddSettledNetUpToTodayToInitialBalance() {
        GetAccountBalancesUseCase useCase = new GetAccountBalancesUseCase(accountBalanceQueryPort, Clock.fixed(NOW, ZoneOffset.UTC));
        FinancialAccount withMovements = FinancialAccount.create("Caixa", AccountType.OPERATIONAL_LEDGER, Money.brl("1000.00"), NOW);
        FinancialAccount withoutMovements = FinancialAccount.create("Tesouraria", AccountType.TREASURY_LEDGER, Money.brl("200.00"), NOW);
        when(accountBalanceQueryPort.settledNetByAccount(List.of(withMovements.id(), withoutMovements.id()), LocalDate.of(2026, 11, 21)))
                .thenReturn(Map.of(withMovements.id(), new BigDecimal("-350.00")));

        Map<java.util.UUID, BigDecimal> balances = useCase.currentBalances(List.of(withMovements, withoutMovements));

        assertThat(balances.get(withMovements.id())).isEqualByComparingTo("650.00");
        assertThat(balances.get(withoutMovements.id())).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldNotQueryWhenThereAreNoAccounts() {
        new GetAccountBalancesUseCase(accountBalanceQueryPort, Clock.fixed(NOW, ZoneOffset.UTC)).currentBalances(List.of());
        verifyNoInteractions(accountBalanceQueryPort);
    }
}
