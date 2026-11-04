package com.emanudev.gestaofinanceira.entry.domain;

import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialEntryTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID COUNTERPARTY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    @Test
    @DisplayName("Should settle PAYABLE entry in full with interest, late fee and discount without residual child")
    void shouldSettlePayableEntryInFull() {
        FinancialEntry entry = FinancialEntry.create(
                "idem-001",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                "Fornecedor de Servidores",
                TODAY,
                TODAY.plusDays(10),
                Money.brl("1000.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        SettlementCommand command = new SettlementCommand(
                Money.brl("1000.00"),
                Money.brl("50.00"),
                Money.brl("20.00"),
                Money.brl("10.00"),
                TODAY,
                false
        );

        SettlementOutcome outcome = entry.settle(
                command,
                false,
                "Fornecedores Operacionais",
                entry.originalAmount(),
                0,
                TODAY,
                NOW.plusSeconds(60)
        );

        assertThat(outcome.hasResidualChild()).isFalse();
        FinancialEntry settled = outcome.settledEntry();
        assertThat(settled.status()).isEqualTo(EntryStatus.PAID);
        assertThat(settled.originalAmount()).isEqualTo(Money.brl("1000.00"));
        assertThat(settled.settledAmount()).isEqualTo(Money.brl("980.00"));
        assertThat(settled.discountAmount()).isEqualTo(Money.brl("50.00"));
        assertThat(settled.interestAmount()).isEqualTo(Money.brl("20.00"));
        assertThat(settled.lateFeeAmount()).isEqualTo(Money.brl("10.00"));
        assertThat(settled.settlementDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("Should settle RECEIVABLE entry partially and automatically generate residual child split entry")
    void shouldSettlePartiallyWithAutomaticResidualSplit() {
        FinancialEntry root = FinancialEntry.create(
                "idem-002",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.RECEIVABLE,
                "Mensalidade Contrato B2B",
                TODAY,
                TODAY.plusDays(5),
                Money.brl("1000.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        SettlementCommand partialCommand = SettlementCommand.partialWithSplit(Money.brl("600.00"), TODAY);

        SettlementOutcome outcome = root.settle(
                partialCommand,
                false,
                "Receita de Mensalidades / Contratos",
                root.originalAmount(),
                0,
                TODAY,
                NOW.plusSeconds(120)
        );

        assertThat(outcome.hasResidualChild()).isTrue();
        FinancialEntry settledParent = outcome.settledEntry();
        FinancialEntry residualChild = outcome.residualChildEntry().orElseThrow();

        assertThat(settledParent.status()).isEqualTo(EntryStatus.RECEIVED);
        assertThat(settledParent.settledAmount()).isEqualTo(Money.brl("600.00"));

        assertThat(residualChild.id()).isNotEqualTo(root.id());
        assertThat(residualChild.parentEntryId()).isEqualTo(root.id());
        assertThat(residualChild.rootEntryId()).isEqualTo(root.id());
        assertThat(residualChild.status()).isEqualTo(EntryStatus.PENDING);
        assertThat(residualChild.originalAmount()).isEqualTo(Money.brl("400.00"));
        assertThat(residualChild.settledAmount()).isNull();
        assertThat(residualChild.description()).isEqualTo("Mensalidade Contrato B2B (Saldo Residual)");
    }

    @Test
    @DisplayName("Should preserve exact principal sum across multiple chained partial splits without rounding drift")
    void shouldPreserveExactSumAcrossMultipleChainedSplits() {
        FinancialEntry root = FinancialEntry.create(
                "idem-003",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.RECEIVABLE,
                "Fatura Trimestral",
                TODAY,
                TODAY.plusDays(15),
                Money.brl("1000.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        SettlementOutcome split1 = root.settle(
                SettlementCommand.partialWithSplit(Money.brl("333.33"), TODAY),
                false,
                "Serviços",
                root.originalAmount(),
                0,
                TODAY,
                NOW.plusSeconds(10)
        );
        FinancialEntry child1 = split1.residualChildEntry().orElseThrow();
        assertThat(child1.originalAmount()).isEqualTo(Money.brl("666.67"));

        SettlementOutcome split2 = child1.settle(
                SettlementCommand.partialWithSplit(Money.brl("333.33"), TODAY),
                false,
                "Serviços",
                root.originalAmount(),
                0,
                TODAY,
                NOW.plusSeconds(20)
        );
        FinancialEntry child2 = split2.residualChildEntry().orElseThrow();
        assertThat(child2.originalAmount()).isEqualTo(Money.brl("333.34"));
        assertThat(child2.parentEntryId()).isEqualTo(child1.id());
        assertThat(child2.rootEntryId()).isEqualTo(root.id());

        SettlementOutcome finalSettle = child2.settle(
                SettlementCommand.full(Money.brl("333.34"), TODAY),
                false,
                "Serviços",
                root.originalAmount(),
                0,
                TODAY,
                NOW.plusSeconds(30)
        );
        assertThat(finalSettle.hasResidualChild()).isFalse();

        Money totalSettled = split1.settledEntry().settledAmount()
                .add(split2.settledEntry().settledAmount())
                .add(finalSettle.settledEntry().settledAmount());

        assertThat(totalSettled).isEqualTo(root.originalAmount());
        assertThat(totalSettled.amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should enforce fiscal attachment lock for PAYABLE in 'Impostos' category regardless of amount")
    void shouldEnforceFiscalAttachmentLockForTaxCategory() {
        FinancialEntry taxEntry = FinancialEntry.create(
                "idem-tax",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                "DARF IRPJ",
                TODAY,
                TODAY.plusDays(3),
                Money.brl("150.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        SettlementCommand command = SettlementCommand.full(Money.brl("150.00"), TODAY);

        assertThatThrownBy(() -> taxEntry.settle(
                command,
                true,
                "Impostos",
                taxEntry.originalAmount(),
                0,
                TODAY,
                NOW
        ))
                .isInstanceOf(FiscalAttachmentRequiredException.class)
                .extracting(ex -> ((FiscalAttachmentRequiredException) ex).getErrorCode())
                .isEqualTo(FiscalAttachmentRequiredException.ERROR_CODE);

        SettlementOutcome allowedWhenAttached = taxEntry.settle(
                command,
                true,
                "Impostos",
                taxEntry.originalAmount(),
                1,
                TODAY,
                NOW
        );
        assertThat(allowedWhenAttached.settledEntry().status()).isEqualTo(EntryStatus.PAID);
    }

    @Test
    @DisplayName("Should enforce fiscal attachment lock for PAYABLE > 5000.00 and block split bypass via rootOriginalAmount")
    void shouldEnforceFiscalAttachmentLockAbove5000AndBlockSplitBypass() {
        FinancialEntry highValueEntry = FinancialEntry.create(
                "idem-high",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                "Equipamentos Datacenter",
                TODAY,
                TODAY.plusDays(10),
                Money.brl("5000.01"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        assertThatThrownBy(() -> highValueEntry.settle(
                SettlementCommand.full(Money.brl("5000.01"), TODAY),
                false,
                "Infraestrutura e Cloud",
                highValueEntry.originalAmount(),
                0,
                TODAY,
                NOW
        )).isInstanceOf(FiscalAttachmentRequiredException.class);

        // Simulate a child split of 2500.00 whose root entry had originalAmount = 6000.00 (> 5000.00)
        UUID rootId = UUID.randomUUID();
        FinancialEntry splitChildUnder5000 = new FinancialEntry(
                UUID.randomUUID(),
                null,
                rootId,
                rootId,
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                EntryStatus.PENDING,
                "Equipamentos Datacenter (Saldo Residual)",
                TODAY,
                TODAY.plusDays(10),
                null,
                Money.brl("2500.00"),
                null,
                Money.zeroBrl(),
                Money.zeroBrl(),
                Money.zeroBrl(),
                null,
                null,
                null,
                ACTOR_ID,
                0L,
                NOW,
                NOW
        );

        assertThatThrownBy(() -> splitChildUnder5000.settle(
                SettlementCommand.full(Money.brl("2500.00"), TODAY),
                false,
                "Infraestrutura e Cloud",
                Money.brl("6000.00"),
                0,
                TODAY,
                NOW
        ))
                .isInstanceOf(FiscalAttachmentRequiredException.class)
                .hasMessageContaining("5000.00");
    }

    @Test
    @DisplayName("Should transition PENDING entry to OVERDUE when dueDate is before today")
    void shouldTransitionPendingToOverdueWhenPastDue() {
        FinancialEntry entry = FinancialEntry.create(
                "idem-overdue",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                "Aluguel Escritório",
                TODAY,
                TODAY.plusDays(1),
                Money.brl("3000.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        assertThat(entry.status()).isEqualTo(EntryStatus.PENDING);

        // On dueDate itself, still PENDING
        FinancialEntry onDueDate = entry.markOverdue(TODAY.plusDays(1), NOW.plusSeconds(3600));
        assertThat(onDueDate.status()).isEqualTo(EntryStatus.PENDING);

        // After dueDate, transitions to OVERDUE
        FinancialEntry afterDueDate = entry.markOverdue(TODAY.plusDays(2), NOW.plusSeconds(7200));
        assertThat(afterDueDate.status()).isEqualTo(EntryStatus.OVERDUE);
    }

    @Test
    @DisplayName("Should soft-delete (cancel) open entry with valid reason and reject settled or active-children cancellation")
    void shouldCancelOpenEntryAndEnforceInvariants() {
        FinancialEntry entry = FinancialEntry.create(
                "idem-cancel",
                ACCOUNT_ID,
                CATEGORY_ID,
                COUNTERPARTY_ID,
                EntryType.PAYABLE,
                "Assinatura Software",
                TODAY,
                TODAY.plusDays(7),
                Money.brl("800.00"),
                ACTOR_ID,
                TODAY,
                NOW
        );

        // Short reason rejected
        assertThatThrownBy(() -> entry.cancel("abc", ACTOR_ID, NOW, false))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getErrorCode())
                .isEqualTo("INVALID_CANCELLATION_REASON");

        // Active children rejected
        assertThatThrownBy(() -> entry.cancel("Cancelamento por duplicidade", ACTOR_ID, NOW, true))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getErrorCode())
                .isEqualTo("ENTRY_HAS_ACTIVE_CHILDREN");

        // Valid soft-delete
        FinancialEntry cancelled = entry.cancel("Lançamento duplicado por erro operacional", ACTOR_ID, NOW.plusSeconds(30), false);
        assertThat(cancelled.status()).isEqualTo(EntryStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("Lançamento duplicado por erro operacional");
        assertThat(cancelled.cancelledBy()).isEqualTo(ACTOR_ID);
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW.plusSeconds(30));

        // Cannot settle a CANCELLED entry
        assertThatThrownBy(() -> cancelled.settle(
                SettlementCommand.full(Money.brl("800.00"), TODAY),
                false,
                "Fornecedores Operacionais",
                cancelled.originalAmount(),
                0,
                TODAY,
                NOW
        )).isInstanceOf(BusinessRuleViolationException.class);
    }
}
