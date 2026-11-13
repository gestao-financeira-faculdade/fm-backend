package com.emanudev.gestaofinanceira.audit.application.usecase;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.out.AuditLogQueryPort;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryUseCaseTest {

    @Mock
    private AuditLogQueryPort auditLogQueryPort;

    private AuditLogQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AuditLogQueryUseCase(auditLogQueryPort);
    }

    @Test
    void shouldQueryPaginatedAuditLogsWithDefaultFilterWhenNull() {
        AuditLogItemResponse item = new AuditLogItemResponse(
                UUID.randomUUID(), "FINANCIAL_ENTRY", UUID.randomUUID(), AuditAction.CREATE,
                UUID.randomUUID(), "Admin", "ADMIN", "USER", "127.0.0.1", "corr-1",
                null, "{}", Instant.parse("2026-10-26T15:00:00Z")
        );
        PagedResult<AuditLogItemResponse> expected = PagedResult.of(List.of(item), 1L, 0, 20);
        when(auditLogQueryPort.queryAuditLogs(AuditLogFilter.empty(), PageQuery.of(0, 20))).thenReturn(expected);

        PagedResult<AuditLogItemResponse> result = useCase.queryAuditLogs(null, null);

        assertThat(result).isEqualTo(expected);
        verify(auditLogQueryPort).queryAuditLogs(AuditLogFilter.empty(), PageQuery.of(0, 20));
    }

    @Test
    void shouldReturnAuditHistoryForSpecificEntry() {
        UUID entryId = UUID.randomUUID();
        when(auditLogQueryPort.getEntryAuditHistory(entryId)).thenReturn(List.of());

        assertThat(useCase.getEntryAuditHistory(entryId)).isEmpty();
        verify(auditLogQueryPort).getEntryAuditHistory(entryId);
    }
}
