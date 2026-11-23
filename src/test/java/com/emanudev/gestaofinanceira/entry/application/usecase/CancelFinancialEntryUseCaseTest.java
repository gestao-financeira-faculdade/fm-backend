package com.emanudev.gestaofinanceira.entry.application.usecase;

import com.emanudev.gestaofinanceira.entry.application.FinancialEntryRepositoryPort;
import com.emanudev.gestaofinanceira.entry.domain.EntryType;
import com.emanudev.gestaofinanceira.entry.domain.FinancialEntry;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditTrailPort;
import com.emanudev.gestaofinanceira.shared.domain.Money;
import com.emanudev.gestaofinanceira.shared.domain.exception.BusinessRuleViolationException;
import com.emanudev.gestaofinanceira.shared.domain.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelFinancialEntryUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Mock
    private FinancialEntryRepositoryPort entryRepository;
    @Mock
    private AuditTrailPort auditTrailPort;
    @Mock
    private ActorContextPort actorContextPort;

    private CancelFinancialEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelFinancialEntryUseCase(entryRepository, auditTrailPort, actorContextPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("DELETE repetido num lançamento já cancelado é idempotente: não grava nem audita de novo")
    void shouldBeIdempotentForAlreadyCancelledEntry() {
        FinancialEntry cancelled = entry().cancel("Lançamento duplicado", UUID.randomUUID(), NOW);
        when(entryRepository.findById(cancelled.id())).thenReturn(Optional.of(cancelled));

        assertThat(useCase.execute(cancelled.id(), "Outro motivo qualquer")).isEqualTo(cancelled);

        verify(entryRepository, never()).save(any());
        verify(auditTrailPort).skipAuditForCurrentAction();
        verify(auditTrailPort, never()).recordTransition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lançamento inexistente continua 404")
    void shouldAnswerNotFoundForUnknownEntry() {
        UUID id = UUID.randomUUID();
        when(entryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "Motivo válido")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("DEF-46: pai pago com filho residual ativo devolve ENTRY_HAS_ACTIVE_CHILDREN")
    void shouldReportActiveChildrenBeforeSettledStatus() {
        FinancialEntry parent = entry();
        when(entryRepository.findById(parent.id())).thenReturn(Optional.of(parent));
        when(entryRepository.hasActiveChildren(parent.id())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(parent.id(), "Motivo válido"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("ENTRY_HAS_ACTIVE_CHILDREN");
    }

    private static FinancialEntry entry() {
        return FinancialEntry.create(null, UUID.randomUUID(), UUID.randomUUID(), null, EntryType.PAYABLE, "Fatura",
                TODAY, TODAY.plusDays(10), Money.brl("100.00"), null, TODAY, NOW);
    }
}
