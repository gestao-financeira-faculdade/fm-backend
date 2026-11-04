package com.emanudev.gestaofinanceira.audit.adapter.out.persistence;

import com.emanudev.gestaofinanceira.shared.application.ActorContext;
import com.emanudev.gestaofinanceira.shared.application.ActorContextPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPersistenceAdapterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-26T15:30:00Z");

    @Mock
    private SpringDataAuditLogRepository repository;

    @Mock
    private ActorContextPort actorContextPort;

    private AuditPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        adapter = new AuditPersistenceAdapter(repository, actorContextPort, fixedClock);
        when(repository.save(any(AuditLogJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldRecordTransitionWithHumanActorAndBeforeAfterSnapshots() {
        UUID userId = UUID.fromString("0192c8a4-0000-7000-8000-000000000001");
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000099");
        when(actorContextPort.getCurrentActor()).thenReturn(
                ActorContext.user(userId, "Maria Analista", "ANALISTA", "192.168.1.45")
        );
        MDC.put("correlation_id", "corr-abc-123");

        Map<String, Object> previousSnapshot = Map.of(
                "status", "PENDING",
                "originalAmount", new BigDecimal("1500.00")
        );
        Map<String, Object> newSnapshot = Map.of(
                "status", "SETTLED",
                "originalAmount", new BigDecimal("1500.00"),
                "paidAmount", new BigDecimal("1500.00")
        );

        adapter.recordTransition(
                "FINANCIAL_ENTRY",
                entryId,
                AuditAction.SETTLE,
                previousSnapshot,
                newSnapshot
        );

        ArgumentCaptor<AuditLogJpaEntity> captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        AuditLogJpaEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEntityType()).isEqualTo("FINANCIAL_ENTRY");
        assertThat(saved.getEntityId()).isEqualTo(entryId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.SETTLE);
        assertThat(saved.getActorUserId()).isEqualTo(userId);
        assertThat(saved.getActorName()).isEqualTo("Maria Analista");
        assertThat(saved.getActorRole()).isEqualTo("ANALISTA");
        assertThat(saved.getActorType()).isEqualTo("USER");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.45");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-abc-123");
        assertThat(saved.getOccurredAt()).isEqualTo(FIXED_INSTANT);
        assertThat(saved.getPreviousState()).contains("\"status\":\"PENDING\"");
        assertThat(saved.getNewState()).contains("\"status\":\"SETTLED\"");
        assertThat(saved.getPreviousState()).isNotEqualTo(saved.getNewState());
    }

    @Test
    void shouldRecordTransitionWithSystemActorAndNullPreviousStateOnCreate() {
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000077");
        when(actorContextPort.getCurrentActor()).thenReturn(ActorContext.system());
        MDC.put("traceId", "trace-xyz-999");

        Map<String, Object> newSnapshot = Map.of("status", "OVERDUE", "id", entryId.toString());

        adapter.recordTransition(
                "FINANCIAL_ENTRY",
                entryId,
                AuditAction.AUTO_OVERDUE_TRANSITION,
                null,
                newSnapshot
        );

        ArgumentCaptor<AuditLogJpaEntity> captor = ArgumentCaptor.forClass(AuditLogJpaEntity.class);
        verify(repository).save(captor.capture());

        AuditLogJpaEntity saved = captor.getValue();
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getActorName()).isEqualTo("SYSTEM_SCHEDULER");
        assertThat(saved.getActorRole()).isEqualTo("SYSTEM");
        assertThat(saved.getActorType()).isEqualTo("SYSTEM");
        assertThat(saved.getCorrelationId()).isEqualTo("trace-xyz-999");
        assertThat(saved.getPreviousState()).isNull();
        assertThat(saved.getNewState()).contains("\"status\":\"OVERDUE\"");
        assertThat(saved.getOccurredAt()).isEqualTo(FIXED_INSTANT);
    }
}
