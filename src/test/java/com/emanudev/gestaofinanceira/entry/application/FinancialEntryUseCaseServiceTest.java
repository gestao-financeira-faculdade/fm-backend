package com.emanudev.gestaofinanceira.entry.application;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryUseCaseService.CreateEntryCommand;
import com.emanudev.gestaofinanceira.entry.domain.EntryStatus;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.entry.domain.FiscalAttachment;
import com.emanudev.gestaofinanceira.entry.domain.SettlementCommand;
import com.emanudev.gestaofinanceira.entry.domain.SettlementOutcome;
import com.emanudev.gestaofinanceira.reference.application.CategoryRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.CounterpartyRepositoryPort;
import com.emanudev.gestaofinanceira.reference.application.FinancialAccountRepositoryPort;
import com.emanudev.gestaofinanceira.reference.domain.AccountType;
import com.emanudev.gestaofinanceira.reference.domain.Category;
import com.emanudev.gestaofinanceira.reference.domain.CategoryType;
import com.emanudev.gestaofinanceira.reference.domain.FinancialAccount;
import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialEntryUseCaseServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T15:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.ofInstant(FIXED_INSTANT, ZONE);

    @Mock
    private FinancialEntryRepositoryPort entryRepository;
    @Mock
    private FinancialEntryQueryPort entryQueryPort;
    @Mock
    private FiscalAttachmentRepositoryPort attachmentRepository;
    @Mock
    private AttachmentStoragePort attachmentStoragePort;
    @Mock
    private FinancialAccountRepositoryPort accountRepository;
    @Mock
    private CategoryRepositoryPort categoryRepository;
    @Mock
    private CounterpartyRepositoryPort counterpartyRepository;
    @Mock
    private AuditTrailPort auditTrailPort;
    @Mock
    private ActorContextPort actorContextPort;

    private FinancialEntryUseCaseService service;
    private FinancialAccount activeAccount;
    private Category operationalCategory;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZONE);
        service = new FinancialEntryUseCaseService(
                entryRepository,
                entryQueryPort,
                attachmentRepository,
                attachmentStoragePort,
                accountRepository,
                categoryRepository,
                counterpartyRepository,
                auditTrailPort,
                actorContextPort,
                clock
        );
        activeAccount = FinancialAccount.create("Caixa Matriz", AccountType.OPERATIONAL_LEDGER, Money.brl("100000.00"), FIXED_INSTANT);
        operationalCategory = Category.create("Fornecedores Operacionais", CategoryType.PAYABLE, false, false, FIXED_INSTANT);
    }

    @Test
    @DisplayName("Should create entry and record CREATE audit transition, and return existing on idempotency hit")
    void shouldCreateEntryAndHandleIdempotency() {
        CreateEntryCommand command = new CreateEntryCommand(
                "idem-key-123",
                activeAccount.id(),
                operationalCategory.id(),
                null,
                EntryType.PAYABLE,
                "Fatura Cloud",
                TODAY,
                TODAY.plusDays(10),
                Money.brl("1200.00")
        );

        when(entryRepository.findByIdempotencyKey("idem-key-123")).thenReturn(Optional.empty());
        when(accountRepository.findById(activeAccount.id())).thenReturn(Optional.of(activeAccount));
        when(categoryRepository.findById(operationalCategory.id())).thenReturn(Optional.of(operationalCategory));
        when(actorContextPort.getCurrentActor()).thenReturn(ActorContext.user(UUID.randomUUID(), "analista", "ANALISTA", "127.0.0.1"));
        when(entryRepository.save(any(FinancialEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialEntry created = service.createEntry(command);

        assertThat(created.status()).isEqualTo(EntryStatus.PENDING);
        assertThat(created.originalAmount()).isEqualTo(Money.brl("1200.00"));
        verify(auditTrailPort).recordTransition(eq("FINANCIAL_ENTRY"), eq(created.id()), eq(AuditAction.CREATE), eq(null), eq(created));

        // Second call with same idempotencyKey returns existing without saving again
        when(entryRepository.findByIdempotencyKey("idem-key-123")).thenReturn(Optional.of(created));
        FinancialEntry duplicateCall = service.createEntry(command);
        assertThat(duplicateCall.id()).isEqualTo(created.id());
        verify(entryRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should record PARTIAL_SETTLE_SPLIT audit action when settling entry partially")
    void shouldRecordPartialSettleSplitAuditOnUnderpayment() {
        FinancialEntry existing = FinancialEntry.create(
                null,
                activeAccount.id(),
                operationalCategory.id(),
                null,
                EntryType.PAYABLE,
                "Fornecedor TI",
                TODAY,
                TODAY.plusDays(5),
                Money.brl("2000.00"),
                UUID.randomUUID(),
                TODAY,
                FIXED_INSTANT
        );

        when(entryRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(operationalCategory.id())).thenReturn(Optional.of(operationalCategory));
        when(attachmentRepository.countByEntryOrRootEntry(existing.id(), existing.id())).thenReturn(0);
        when(entryRepository.save(any(FinancialEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementOutcome outcome = service.settleEntry(
                existing.id(),
                SettlementCommand.partialWithSplit(Money.brl("1200.00"), TODAY)
        );

        assertThat(outcome.hasResidualChild()).isTrue();
        assertThat(outcome.settledEntry().status()).isEqualTo(EntryStatus.PAID);
        assertThat(outcome.residualChildEntry().orElseThrow().originalAmount()).isEqualTo(Money.brl("800.00"));

        verify(entryRepository, times(2)).save(any(FinancialEntry.class));
        verify(auditTrailPort).recordTransition(
                eq("FINANCIAL_ENTRY"),
                eq(existing.id()),
                eq(AuditAction.PARTIAL_SETTLE_SPLIT),
                eq(existing),
                any()
        );
    }

    @Test
    @DisplayName("Should upload valid PDF attachment, store on AttachmentStoragePort and record UPLOAD_ATTACHMENT audit")
    void shouldUploadValidAttachmentAndRecordAudit() {
        FinancialEntry existing = FinancialEntry.create(
                null,
                activeAccount.id(),
                operationalCategory.id(),
                null,
                EntryType.PAYABLE,
                "Nota Fiscal Acima de 5k",
                TODAY,
                TODAY.plusDays(5),
                Money.brl("7500.00"),
                UUID.randomUUID(),
                TODAY,
                FIXED_INSTANT
        );

        byte[] pdfBytes = "%PDF-1.7\nfiscal-invoice".getBytes(StandardCharsets.US_ASCII);
        when(entryRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(attachmentStoragePort.store(eq(existing.id()), any(UUID.class), eq("nf_7500.pdf"), eq(pdfBytes)))
                .thenReturn(existing.id() + "/att_nf_7500.pdf");
        when(attachmentRepository.save(any(FiscalAttachment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(actorContextPort.getCurrentActor()).thenReturn(ActorContext.user(UUID.randomUUID(), "analista", "ANALISTA", "127.0.0.1"));

        FiscalAttachment saved = service.uploadAttachment(existing.id(), "nf_7500.pdf", pdfBytes);

        assertThat(saved.contentType()).isEqualTo("application/pdf");
        assertThat(saved.fileSizeBytes()).isEqualTo(pdfBytes.length);
        verify(auditTrailPort).recordTransition(
                eq("FINANCIAL_ENTRY"),
                eq(existing.id()),
                eq(AuditAction.UPLOAD_ATTACHMENT),
                eq(existing),
                eq(saved)
        );
    }

    @Test
    @DisplayName("Should mark overdue candidates and record AUTO_OVERDUE_TRANSITION audit trail")
    void shouldMarkOverdueCandidatesAndRecordAudit() {
        FinancialEntry pastDue = new FinancialEntry(
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                activeAccount.id(),
                operationalCategory.id(),
                null,
                EntryType.PAYABLE,
                EntryStatus.PENDING,
                "Boleto Vencido",
                TODAY.minusDays(5),
                TODAY.minusDays(1),
                null,
                Money.brl("500.00"),
                null,
                Money.zeroBrl(),
                Money.zeroBrl(),
                Money.zeroBrl(),
                null,
                null,
                null,
                null,
                0L,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        when(entryRepository.findOverdueCandidates(TODAY)).thenReturn(List.of(pastDue));
        when(entryRepository.save(any(FinancialEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.markOverdueEntries();

        assertThat(count).isEqualTo(1);
        verify(auditTrailPort).recordTransition(
                eq("FINANCIAL_ENTRY"),
                eq(pastDue.id()),
                eq(AuditAction.AUTO_OVERDUE_TRANSITION),
                eq(pastDue),
                any(FinancialEntry.class)
        );
        verify(entryRepository, never()).findById(any());
    }
}
