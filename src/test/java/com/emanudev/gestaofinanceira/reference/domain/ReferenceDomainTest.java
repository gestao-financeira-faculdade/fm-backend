package com.emanudev.gestaofinanceira.reference.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceDomainTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void shouldCreateFinancialAccountAndTrimName() {
        FinancialAccount account = FinancialAccount.create(
                "  Conta Corrente Principal  ",
                AccountType.OPERATIONAL_LEDGER,
                Money.brl("25000.00"),
                FIXED_NOW
        );

        assertThat(account.id()).isNotNull();
        assertThat(account.name()).isEqualTo("Conta Corrente Principal");
        assertThat(account.accountType()).isEqualTo(AccountType.OPERATIONAL_LEDGER);
        assertThat(account.initialBalance()).isEqualTo(Money.brl("25000.00"));
        assertThat(account.active()).isTrue();
    }

    @Test
    void shouldRejectBlankFinancialAccountName() {
        assertThatThrownBy(() -> FinancialAccount.create("   ", AccountType.PETTY_CASH, Money.zeroBrl(), FIXED_NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Account name must not be blank");
    }

    @Test
    void shouldForceMandatoryAttachmentWhenCategoryNameIsImpostos() {
        Category category = Category.create("  impostos ", CategoryType.PAYABLE, false, false, FIXED_NOW);

        assertThat(category.name()).isEqualTo("impostos");
        assertThat(category.requiresAttachmentAlways()).isTrue();
        assertThat(category.requiresMandatoryFiscalAttachment()).isTrue();
    }

    @Test
    void shouldRejectBlankCategoryName() {
        assertThatThrownBy(() -> Category.create("", CategoryType.RECEIVABLE, false, false, FIXED_NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Category name must not be blank");
    }

    @Test
    void shouldCreateCounterpartyAndNormalizeBlankOptionalFieldsToNull() {
        Counterparty counterparty = Counterparty.create(
                "  Fornecedor Alpha LTDA ",
                "   ",
                " 12.345.678/0001-90 ",
                CounterpartyType.SUPPLIER,
                FIXED_NOW
        );

        assertThat(counterparty.id()).isNotNull();
        assertThat(counterparty.legalName()).isEqualTo("Fornecedor Alpha LTDA");
        assertThat(counterparty.tradeName()).isNull();
        assertThat(counterparty.taxId()).isEqualTo("12.345.678/0001-90");
    }

    @Test
    void shouldRejectNullCounterpartyType() {
        assertThatThrownBy(() -> new Counterparty(
                UUID.randomUUID(), "Empresa X", null, null, null, true, FIXED_NOW, FIXED_NOW
        )).isInstanceOf(BusinessRuleViolationException.class);
    }
}
