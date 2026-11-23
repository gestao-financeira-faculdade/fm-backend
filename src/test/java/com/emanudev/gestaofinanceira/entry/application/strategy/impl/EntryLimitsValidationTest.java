package com.emanudev.gestaofinanceira.entry.application.strategy.impl;

import com.emanudev.gestaofinanceira.entry.application.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.application.strategy.CreateEntryValidationContext;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryLimitsValidationTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final FinancialAccount BRL_ACCOUNT =
            FinancialAccount.create("Caixa", AccountType.OPERATIONAL_LEDGER, Money.brl("0.00"), NOW);
    private static final Category CATEGORY = Category.create("Fornecedores", CategoryType.PAYABLE, false, false, NOW);

    private final MaximumAmountValidationImpl maximum = new MaximumAmountValidationImpl(new BigDecimal("10000000.00"));
    private final AccountCurrencyValidationImpl currency = new AccountCurrencyValidationImpl();

    @Test
    @DisplayName("Teto de R$ 10 milhões: o limite exato passa e um centavo acima é recusado com 422")
    void shouldRejectAmountAboveConfiguredMaximum() {
        assertThatCode(() -> maximum.execute(context(Money.brl("10000000.00")))).doesNotThrowAnyException();

        assertThatThrownBy(() -> maximum.execute(context(Money.brl("10000000.01"))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo(MaximumAmountValidationImpl.ERROR_CODE);
    }

    @Test
    @DisplayName("DEF-12: lançamento em USD numa conta em BRL é recusado")
    void shouldRejectCurrencyDifferentFromAccount() {
        assertThatCode(() -> currency.execute(context(Money.brl("10.00")))).doesNotThrowAnyException();

        assertThatThrownBy(() -> currency.execute(context(Money.of(new BigDecimal("10.00"), "USD"))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo(AccountCurrencyValidationImpl.ERROR_CODE);
    }

    private static CreateEntryValidationContext context(Money amount) {
        CreateEntryCommand command = new CreateEntryCommand(
                null, BRL_ACCOUNT.id(), CATEGORY.id(), null, EntryType.PAYABLE, "Teste",
                LocalDate.of(2026, 11, 19), LocalDate.of(2026, 12, 19), amount
        );
        return new CreateEntryValidationContext(command, BRL_ACCOUNT, CATEGORY);
    }
}
