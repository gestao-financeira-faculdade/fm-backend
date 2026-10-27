package com.emanudev.gestaofinanceira.audit.adapter.in.web;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.in.QueryAuditLogsUseCase;
import com.emanudev.gestaofinanceira.shared.adapter.in.web.GlobalExceptionHandler;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private QueryAuditLogsUseCase queryAuditLogsUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuditLogController controller = new AuditLogController(queryAuditLogsUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnPaginatedAuditLogsWithFilters() throws Exception {
        UUID logId = UUID.fromString("0192c8a4-0000-7000-8000-000000000101");
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000202");
        UUID userId = UUID.fromString("0192c8a4-0000-7000-8000-000000000303");
        Instant occurredAt = Instant.parse("2026-10-26T18:00:00Z");

        AuditLogItemResponse item = new AuditLogItemResponse(
                logId,
                "FINANCIAL_ENTRY",
                entryId,
                AuditAction.SETTLE,
                userId,
                "Carlos Gestor",
                "ADMIN",
                "USER",
                "10.0.0.15",
                "corr-777",
                "{\"status\":\"PENDING\"}",
                "{\"status\":\"SETTLED\"}",
                occurredAt
        );

        when(queryAuditLogsUseCase.queryAuditLogs(any(AuditLogFilter.class), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(item), 1L, 0, 10));

        mockMvc.perform(get("/v1/audit-logs")
                        .param("entityType", "FINANCIAL_ENTRY")
                        .param("entityId", entryId.toString())
                        .param("action", "SETTLE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.items[0].entityType").value("FINANCIAL_ENTRY"))
                .andExpect(jsonPath("$.items[0].entityId").value(entryId.toString()))
                .andExpect(jsonPath("$.items[0].action").value("SETTLE"))
                .andExpect(jsonPath("$.items[0].actorName").value("Carlos Gestor"))
                .andExpect(jsonPath("$.items[0].actorRole").value("ADMIN"));

        verify(queryAuditLogsUseCase).queryAuditLogs(
                eq(new AuditLogFilter("FINANCIAL_ENTRY", entryId, null, AuditAction.SETTLE, null, null)),
                eq(PageQuery.of(0, 10))
        );
    }

    @Test
    void shouldReturnAuditHistoryForSpecificEntry() throws Exception {
        UUID entryId = UUID.fromString("0192c8a4-0000-7000-8000-000000000555");
        UUID log1 = UUID.fromString("0192c8a4-0000-7000-8000-000000000556");
        UUID log2 = UUID.fromString("0192c8a4-0000-7000-8000-000000000557");

        List<AuditLogItemResponse> history = List.of(
                new AuditLogItemResponse(
                        log2,
                        "FINANCIAL_ENTRY",
                        entryId,
                        AuditAction.SETTLE,
                        UUID.randomUUID(),
                        "Maria Analista",
                        "ANALISTA",
                        "USER",
                        "127.0.0.1",
                        "corr-2",
                        "{\"status\":\"PENDING\"}",
                        "{\"status\":\"SETTLED\"}",
                        Instant.parse("2026-10-26T19:00:00Z")
                ),
                new AuditLogItemResponse(
                        log1,
                        "FINANCIAL_ENTRY",
                        entryId,
                        AuditAction.CREATE,
                        UUID.randomUUID(),
                        "Maria Analista",
                        "ANALISTA",
                        "USER",
                        "127.0.0.1",
                        "corr-1",
                        null,
                        "{\"status\":\"PENDING\"}",
                        Instant.parse("2026-10-26T18:00:00Z")
                )
        );

        when(queryAuditLogsUseCase.getEntryAuditHistory(entryId)).thenReturn(history);

        mockMvc.perform(get("/v1/entries/{entryId}/audit-history", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(log2.toString()))
                .andExpect(jsonPath("$[0].action").value("SETTLE"))
                .andExpect(jsonPath("$[1].id").value(log1.toString()))
                .andExpect(jsonPath("$[1].action").value("CREATE"));

        verify(queryAuditLogsUseCase).getEntryAuditHistory(entryId);
    }
}
