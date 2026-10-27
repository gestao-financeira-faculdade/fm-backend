package com.emanudev.gestaofinanceira.audit.adapter.in.web;

import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogFilter;
import com.emanudev.gestaofinanceira.audit.application.port.in.AuditLogItemResponse;
import com.emanudev.gestaofinanceira.audit.application.port.in.QueryAuditLogsUseCase;
import com.emanudev.gestaofinanceira.shared.application.AuditAction;
import com.emanudev.gestaofinanceira.shared.application.PageQuery;
import com.emanudev.gestaofinanceira.shared.application.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditLogController {

    private final QueryAuditLogsUseCase queryAuditLogsUseCase;

    public AuditLogController(QueryAuditLogsUseCase queryAuditLogsUseCase) {
        this.queryAuditLogsUseCase = queryAuditLogsUseCase;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<PagedResult<AuditLogItemResponse>> listAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuditLogFilter filter = new AuditLogFilter(
                entityType,
                entityId,
                actorUserId,
                action,
                startDate,
                endDate
        );
        PageQuery pageQuery = PageQuery.of(page, size);
        PagedResult<AuditLogItemResponse> result = queryAuditLogsUseCase.queryAuditLogs(filter, pageQuery);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/entries/{entryId}/audit-history")
    public ResponseEntity<List<AuditLogItemResponse>> getEntryAuditHistory(@PathVariable UUID entryId) {
        List<AuditLogItemResponse> history = queryAuditLogsUseCase.getEntryAuditHistory(entryId);
        return ResponseEntity.ok(history);
    }
}
